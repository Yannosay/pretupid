use std::cmp::Ordering;

use pretupid_core::instance::{Instance, InstanceMod, ModLoader, NewInstanceMod};
use serde::{Deserialize, Serialize};
use tauri::State;

use super::content::{add_instance_mod, content_dir, load_instance, InstallResult};
use super::util::{self, ExpectedHash};
use crate::state::AppState;

pub const PROJECT_SLUG: &str = "pretupid-client";
const UPDATE_ENDPOINT: &str = "https://pretupid.yannosay.com/api/client/v1/updates";
const RELEASE_HOSTS: [&str; 3] = [
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
];
const PERFORMANCE_PACK: [&str; 5] = [
    "sodium",
    "lithium",
    "ferrite-core",
    "immediatelyfast",
    "entityculling",
];
const FILE_PREFIX: &str = "pretupid-client-";

#[derive(Debug, Serialize)]
pub struct PretupidClientStatus {
    pub supported: bool,
    pub installed: bool,
    pub enabled: bool,
    pub mod_id: Option<String>,
    pub version_name: Option<String>,
    pub latest_version: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ClientRelease {
    available: bool,
    #[serde(default)]
    version: String,
    #[serde(default)]
    url: String,
    #[serde(default)]
    sha1: String,
    #[serde(default)]
    sha512: String,
}

pub struct ReleaseInfo {
    pub version: String,
    url: String,
    sha1: String,
    sha512: String,
}

pub fn supports_client(instance: &Instance) -> bool {
    matches!(
        instance.mod_loader,
        ModLoader::Fabric | ModLoader::Quilt | ModLoader::Modrinth
    )
}

pub fn launch_properties(instance: &Instance) -> Vec<String> {
    if !supports_client(instance) {
        return Vec::new();
    }
    vec![
        "-Dpretupid.launcher=true".to_string(),
        format!("-Dpretupid.launcher.version={}", env!("CARGO_PKG_VERSION")),
    ]
}

fn is_client_entry(entry: &InstanceMod) -> bool {
    entry.project_type == "mod"
        && (entry.project_id == PROJECT_SLUG
            || entry
                .file_name
                .as_deref()
                .is_some_and(|name| name.to_ascii_lowercase().starts_with(FILE_PREFIX)))
}

fn find_installed(mods: &[InstanceMod]) -> Option<&InstanceMod> {
    mods.iter().find(|entry| is_client_entry(entry))
}

fn compare_versions(left: &str, right: &str) -> Ordering {
    fn parse(value: &str) -> (Vec<u64>, String) {
        let base = value
            .trim_start_matches('v')
            .split('+')
            .next()
            .unwrap_or_default();
        let (core, stage) = base.split_once('-').unwrap_or((base, ""));
        let numbers = core
            .split('.')
            .map(|part| part.parse::<u64>().unwrap_or(0))
            .collect();
        (numbers, stage.to_string())
    }
    let (a, a_stage) = parse(left);
    let (b, b_stage) = parse(right);
    for index in 0..a.len().max(b.len()) {
        let ordering = a.get(index).unwrap_or(&0).cmp(b.get(index).unwrap_or(&0));
        if ordering != Ordering::Equal {
            return ordering;
        }
    }
    match (a_stage.is_empty(), b_stage.is_empty()) {
        (true, true) => Ordering::Equal,
        (true, false) => Ordering::Greater,
        (false, true) => Ordering::Less,
        (false, false) => a_stage.cmp(&b_stage),
    }
}

fn is_release_host(url: &str) -> bool {
    url::Url::parse(url).is_ok_and(|parsed| {
        parsed.scheme() == "https"
            && parsed
                .host_str()
                .is_some_and(|host| RELEASE_HOSTS.contains(&host))
    })
}

fn is_hex(value: &str, length: usize) -> bool {
    value.len() == length && value.chars().all(|c| c.is_ascii_hexdigit())
}

pub async fn fetch_release(minecraft_version: &str) -> Result<Option<ReleaseInfo>, String> {
    let client = util::http_client()?;
    let response = client
        .get(UPDATE_ENDPOINT)
        .query(&[("mc", minecraft_version)])
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await
        .map_err(|e| format!("Pretupid update service is unreachable: {e}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "Pretupid update service returned {}",
            response.status()
        ));
    }
    let release: ClientRelease = response.json().await.map_err(|e| e.to_string())?;
    if !release.available {
        return Ok(None);
    }
    if !is_release_host(&release.url) || !is_hex(&release.sha1, 40) || !is_hex(&release.sha512, 128)
    {
        return Err("Pretupid update service returned an untrusted release".to_string());
    }
    Ok(Some(ReleaseInfo {
        version: release.version,
        url: release.url,
        sha1: release.sha1,
        sha512: release.sha512,
    }))
}

async fn install_release(
    state: &AppState,
    instance: &Instance,
    release: &ReleaseInfo,
) -> Result<InstanceMod, String> {
    let dir = content_dir(state, instance, "mod")?;
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|e| e.to_string())?;
    let file_name = util::sanitize_file_name(&format!(
        "{FILE_PREFIX}{}+mc{}.jar",
        release.version, instance.minecraft_version
    ));
    let staging = dir.join(format!(".{file_name}.download"));
    let expected = ExpectedHash {
        sha1: Some(release.sha1.as_str()),
        sha512: Some(release.sha512.as_str()),
    };
    let client = util::http_client()?;
    let sha1 = util::download_verified(&client, &release.url, &staging, &expected).await?;
    let instance_id = instance.id.to_string();
    let mods = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;
    let mut enabled = true;
    for entry in mods.iter().filter(|entry| is_client_entry(entry)) {
        enabled &= entry.enabled;
        if let Some(name) = entry.file_name.as_deref() {
            let safe = util::sanitize_file_name(name);
            for candidate in [safe.clone(), format!("{safe}.disabled")] {
                let _ = tokio::fs::remove_file(dir.join(candidate)).await;
            }
        }
        let _ = state.instances.remove_mod(&entry.id).await;
    }
    let target_name = if enabled {
        file_name.clone()
    } else {
        format!("{file_name}.disabled")
    };
    if let Err(error) = tokio::fs::rename(&staging, dir.join(&target_name)).await {
        let _ = tokio::fs::remove_file(&staging).await;
        return Err(error.to_string());
    }
    let record = NewInstanceMod {
        instance_id,
        project_id: PROJECT_SLUG.to_string(),
        project_type: "mod".to_string(),
        title: "Pretupid Client".to_string(),
        icon_url: None,
        version_id: Some(release.version.clone()),
        version_name: Some(release.version.clone()),
        file_url: Some(release.url.clone()),
        file_name: Some(file_name),
        sha1: Some(sha1),
        enabled,
    };
    state
        .instances
        .add_mod(&record)
        .await
        .map_err(|e| e.to_string())
}

pub async fn update_if_outdated(
    state: &AppState,
    instance: &Instance,
) -> Result<Option<String>, String> {
    if !supports_client(instance) {
        return Ok(None);
    }
    let mods = state
        .instances
        .list_mods(&instance.id.to_string())
        .await
        .map_err(|e| e.to_string())?;
    let Some(installed) = find_installed(&mods) else {
        return Ok(None);
    };
    let Some(release) = fetch_release(&instance.minecraft_version).await? else {
        return Ok(None);
    };
    let current = installed.version_name.clone().unwrap_or_default();
    if !current.is_empty() && compare_versions(&release.version, &current) != Ordering::Greater {
        return Ok(None);
    }
    install_release(state, instance, &release).await?;
    Ok(Some(release.version))
}

#[tauri::command]
pub async fn pretupid_client_status(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<PretupidClientStatus, String> {
    let instance = load_instance(&state, &instance_id).await?;
    if !supports_client(&instance) {
        return Ok(PretupidClientStatus {
            supported: false,
            installed: false,
            enabled: false,
            mod_id: None,
            version_name: None,
            latest_version: None,
        });
    }
    let mods = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;
    let installed = find_installed(&mods);
    let latest = fetch_release(&instance.minecraft_version)
        .await
        .ok()
        .flatten()
        .map(|release| release.version);
    Ok(PretupidClientStatus {
        supported: true,
        installed: installed.is_some(),
        enabled: installed.is_some_and(|entry| entry.enabled),
        mod_id: installed.map(|entry| entry.id.clone()),
        version_name: installed.and_then(|entry| entry.version_name.clone()),
        latest_version: latest,
    })
}

#[tauri::command]
pub async fn install_performance_pack(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<InstallResult, String> {
    let instance = load_instance(&state, &instance_id).await?;
    if !matches!(instance.mod_loader, ModLoader::Fabric | ModLoader::Quilt) {
        return Err("Performance mods are available for Fabric and Quilt instances.".to_string());
    }
    let mut installed = Vec::new();
    let mut warnings = Vec::new();
    for slug in PERFORMANCE_PACK {
        match add_instance_mod(
            state.clone(),
            instance_id.clone(),
            slug.to_string(),
            None,
            Some(true),
        )
        .await
        {
            Ok(result) => {
                installed.extend(result.installed);
                warnings.extend(result.warnings);
            }
            Err(error) if error.contains("already installed") => {}
            Err(error) => warnings.push(error),
        }
    }
    Ok(InstallResult {
        installed,
        warnings,
    })
}

#[tauri::command]
pub async fn install_pretupid_client(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<InstallResult, String> {
    let instance = load_instance(&state, &instance_id).await?;
    if !supports_client(&instance) {
        return Err(
            "Pretupid Client runs on Fabric and Quilt instances. Change the loader of this instance first."
                .to_string(),
        );
    }
    let release = fetch_release(&instance.minecraft_version)
        .await?
        .ok_or_else(|| {
            format!(
                "Pretupid Client is not available for Minecraft {} yet.",
                instance.minecraft_version
            )
        })?;
    let mut warnings = Vec::new();
    if let Err(error) = add_instance_mod(
        state.clone(),
        instance_id.clone(),
        "fabric-api".to_string(),
        None,
        Some(true),
    )
    .await
    {
        if !error.contains("already installed") {
            warnings.push(error);
        }
    }
    let installed = install_release(&state, &instance, &release).await?;
    Ok(InstallResult {
        installed: vec![installed],
        warnings,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn orders_versions() {
        assert_eq!(compare_versions("0.1.0-alpha", "0.0.2-alpha"), Ordering::Greater);
        assert_eq!(compare_versions("0.1.0", "0.1.0-alpha"), Ordering::Greater);
        assert_eq!(compare_versions("0.1.0-alpha+mc26.3", "0.1.0-alpha"), Ordering::Equal);
        assert_eq!(compare_versions("0.1.0-alpha", "0.1.0-beta"), Ordering::Less);
    }

    #[test]
    fn rejects_untrusted_hosts() {
        assert!(is_release_host("https://github.com/Yannosay/pretupid/releases/download/x/a.jar"));
        assert!(!is_release_host("http://github.com/a.jar"));
        assert!(!is_release_host("https://evil.example/a.jar"));
    }
}
