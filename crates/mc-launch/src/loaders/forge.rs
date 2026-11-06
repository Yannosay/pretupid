use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};

use serde::Deserialize;
use tokio::sync::{mpsc, watch};

use crate::download::{self, DownloadTask};
use crate::error::{LaunchError, LaunchResult};
use crate::launcher::LaunchEvent;
use crate::libraries::{self, is_safe_relative_path, maven_path};
use crate::models::{Library, LoaderKind, VersionDetails};
use crate::process;

use super::LoaderVersion;

const FORGE_MAVEN: &str = "https://maven.minecraftforge.net/";
const NEOFORGE_MAVEN: &str = "https://maven.neoforged.net/releases/";
const FORGE_PROMOTIONS: &str =
    "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
const PROFILE_MARKER: &str = "pretupid-complete";

pub struct ForgeArtifact {
    pub repository: &'static str,
    pub group_path: &'static str,
    pub artifact: &'static str,
    pub full_version: String,
}

impl ForgeArtifact {
    pub fn resolve(kind: LoaderKind, mc_version: &str, loader_version: &str) -> LaunchResult<Self> {
        let loader_version = loader_version.trim();
        if loader_version.is_empty() {
            return Err(LaunchError::LaunchFailed(format!(
                "{} needs a loader version. Edit the instance and pick one.",
                kind.label()
            )));
        }
        if !loader_version
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-' || c == '_' || c == '+')
        {
            return Err(LaunchError::LaunchFailed(format!(
                "invalid loader version '{}'",
                loader_version
            )));
        }
        let prefixed = |v: &str| {
            if v.starts_with(&format!("{}-", mc_version)) {
                v.to_string()
            } else {
                format!("{}-{}", mc_version, v)
            }
        };
        match kind {
            LoaderKind::Forge => Ok(Self {
                repository: FORGE_MAVEN,
                group_path: "net/minecraftforge/forge",
                artifact: "forge",
                full_version: prefixed(loader_version),
            }),
            LoaderKind::NeoForge if mc_version == "1.20.1" => Ok(Self {
                repository: NEOFORGE_MAVEN,
                group_path: "net/neoforged/forge",
                artifact: "forge",
                full_version: prefixed(loader_version),
            }),
            LoaderKind::NeoForge => Ok(Self {
                repository: NEOFORGE_MAVEN,
                group_path: "net/neoforged/neoforge",
                artifact: "neoforge",
                full_version: loader_version
                    .strip_prefix(&format!("{}-", mc_version))
                    .unwrap_or(loader_version)
                    .to_string(),
            }),
            _ => Err(LaunchError::LaunchFailed(
                "not a Forge-based loader".to_string(),
            )),
        }
    }

    pub fn installer_url(&self) -> String {
        format!(
            "{}{}/{}/{}-{}-installer.jar",
            self.repository, self.group_path, self.full_version, self.artifact, self.full_version
        )
    }

    fn slug(&self) -> String {
        format!(
            "{}-{}",
            self.group_path.replace('/', "."),
            self.full_version
        )
    }
}

fn extract_xml_versions(xml: &str) -> Vec<String> {
    let mut versions = Vec::new();
    let mut rest = xml;
    while let Some(start) = rest.find("<version>") {
        let after = &rest[start + 9..];
        match after.find("</version>") {
            Some(end) => {
                versions.push(after[..end].trim().to_string());
                rest = &after[end + 10..];
            }
            None => break,
        }
    }
    versions
}

fn compare_numeric(a: &str, b: &str) -> std::cmp::Ordering {
    let parse = |s: &str| -> Vec<u64> {
        s.split(['.', '-', '+'])
            .map(|p| {
                p.chars()
                    .take_while(|c| c.is_ascii_digit())
                    .collect::<String>()
            })
            .filter_map(|p| p.parse::<u64>().ok())
            .collect()
    };
    parse(a).cmp(&parse(b))
}

async fn fetch_text(client: &reqwest::Client, url: &str) -> LaunchResult<String> {
    let response = client
        .get(url)
        .send()
        .await
        .map_err(|e| LaunchError::Network(e.to_string()))?;
    if !response.status().is_success() {
        return Err(LaunchError::Network(format!(
            "{} returned {}",
            url,
            response.status()
        )));
    }
    response
        .text()
        .await
        .map_err(|e| LaunchError::Network(e.to_string()))
}

pub async fn list_forge_versions(
    client: &reqwest::Client,
    mc_version: &str,
) -> LaunchResult<Vec<LoaderVersion>> {
    let xml = fetch_text(
        client,
        &format!("{}net/minecraftforge/forge/maven-metadata.xml", FORGE_MAVEN),
    )
    .await?;
    let prefix = format!("{}-", mc_version);
    let mut versions: Vec<String> = extract_xml_versions(&xml)
        .into_iter()
        .filter_map(|v| v.strip_prefix(&prefix).map(String::from))
        .collect();
    versions.sort_by(|a, b| compare_numeric(b, a));

    let promotions: serde_json::Value = match fetch_text(client, FORGE_PROMOTIONS).await {
        Ok(text) => serde_json::from_str(&text).unwrap_or(serde_json::Value::Null),
        Err(_) => serde_json::Value::Null,
    };
    let promo = |key: &str| {
        promotions
            .get("promos")
            .and_then(|p| p.get(format!("{}-{}", mc_version, key)))
            .and_then(|v| v.as_str())
            .map(String::from)
    };
    let recommended = promo("recommended");
    let latest = promo("latest");

    let mut result: Vec<LoaderVersion> = versions
        .into_iter()
        .map(|version| {
            let base = version.split('-').next().unwrap_or(&version).to_string();
            LoaderVersion {
                recommended: recommended.as_deref() == Some(base.as_str()),
                latest: latest.as_deref() == Some(base.as_str()),
                stable: true,
                version,
            }
        })
        .collect();
    if latest.is_none() {
        if let Some(first) = result.first_mut() {
            first.latest = true;
        }
    }
    Ok(result)
}

fn neoforge_prefix(mc_version: &str) -> Option<String> {
    match mc_version.strip_prefix("1.") {
        Some(rest) => {
            let parts: Vec<&str> = rest.split('.').collect();
            match parts.as_slice() {
                [major] => Some(format!("{}.0.", major)),
                [major, minor] => Some(format!("{}.{}.", major, minor)),
                _ => None,
            }
        }
        None => Some(format!("{}.", mc_version)),
    }
}

pub async fn list_neoforge_versions(
    client: &reqwest::Client,
    mc_version: &str,
) -> LaunchResult<Vec<LoaderVersion>> {
    let mut versions: Vec<String> = if mc_version == "1.20.1" {
        let xml = fetch_text(
            client,
            &format!("{}net/neoforged/forge/maven-metadata.xml", NEOFORGE_MAVEN),
        )
        .await?;
        let prefix = "1.20.1-";
        extract_xml_versions(&xml)
            .into_iter()
            .filter_map(|v| v.strip_prefix(prefix).map(String::from))
            .collect()
    } else {
        let prefix = match neoforge_prefix(mc_version) {
            Some(p) => p,
            None => return Ok(Vec::new()),
        };
        let xml = fetch_text(
            client,
            &format!(
                "{}net/neoforged/neoforge/maven-metadata.xml",
                NEOFORGE_MAVEN
            ),
        )
        .await?;
        extract_xml_versions(&xml)
            .into_iter()
            .filter(|v| v.starts_with(&prefix))
            .collect()
    };
    versions.sort_by(|a, b| compare_numeric(b, a));

    let mut result: Vec<LoaderVersion> = versions
        .into_iter()
        .map(|version| LoaderVersion {
            stable: !version.contains("beta") && !version.contains("alpha"),
            recommended: false,
            latest: false,
            version,
        })
        .collect();
    if let Some(first) = result.first_mut() {
        first.latest = true;
    }
    if let Some(stable) = result.iter_mut().find(|v| v.stable) {
        stable.recommended = true;
    }
    Ok(result)
}

#[derive(Debug, Deserialize)]
struct InstallProfile {
    #[serde(default)]
    json: Option<String>,
    #[serde(default)]
    data: HashMap<String, SidedValue>,
    #[serde(default)]
    processors: Vec<Processor>,
    #[serde(default)]
    libraries: Vec<Library>,
    #[serde(rename = "versionInfo", default)]
    version_info: Option<VersionDetails>,
    #[serde(default)]
    install: Option<LegacyInstall>,
}

#[derive(Debug, Deserialize)]
struct SidedValue {
    #[serde(default)]
    client: String,
}

#[derive(Debug, Deserialize)]
struct Processor {
    #[serde(default)]
    sides: Option<Vec<String>>,
    jar: String,
    #[serde(default)]
    classpath: Vec<String>,
    #[serde(default)]
    args: Vec<String>,
    #[serde(default)]
    outputs: HashMap<String, String>,
}

#[derive(Debug, Deserialize)]
struct LegacyInstall {
    path: String,
    #[serde(rename = "filePath")]
    file_path: String,
}

pub struct ForgeInstallContext<'a> {
    pub client: &'a reqwest::Client,
    pub caches_dir: &'a Path,
    pub libraries_dir: &'a Path,
    pub client_jar: &'a Path,
    pub mc_version: &'a str,
    pub java_path: &'a Path,
    pub verify: bool,
    pub event_tx: &'a mpsc::UnboundedSender<LaunchEvent>,
    pub cancel_rx: &'a watch::Receiver<bool>,
}

impl ForgeInstallContext<'_> {
    fn log(&self, line: impl Into<String>) {
        self.event_tx
            .send(LaunchEvent::Log { line: line.into() })
            .ok();
    }

    fn stage(&self, message: impl Into<String>) {
        self.event_tx
            .send(LaunchEvent::Progress {
                current: 0,
                total: 0,
                message: message.into(),
            })
            .ok();
    }

    fn library_path(&self, coord: &str) -> LaunchResult<PathBuf> {
        let rel = maven_path(coord)
            .ok_or_else(|| LaunchError::Parse(format!("invalid maven coordinate '{}'", coord)))?;
        Ok(self.libraries_dir.join(rel))
    }
}

fn read_zip_entry(
    archive: &mut zip::ZipArchive<std::fs::File>,
    name: &str,
) -> LaunchResult<Option<Vec<u8>>> {
    let mut entry = match archive.by_name(name) {
        Ok(e) => e,
        Err(zip::result::ZipError::FileNotFound) => return Ok(None),
        Err(e) => return Err(LaunchError::Parse(e.to_string())),
    };
    let mut buffer = Vec::with_capacity(entry.size() as usize);
    entry.read_to_end(&mut buffer)?;
    Ok(Some(buffer))
}

fn open_zip(path: &Path) -> LaunchResult<zip::ZipArchive<std::fs::File>> {
    let file = std::fs::File::open(path)?;
    zip::ZipArchive::new(file).map_err(|e| {
        LaunchError::Parse(format!("{} is not a valid archive: {}", path.display(), e))
    })
}

fn extract_maven_folder(
    installer: &Path,
    libraries_dir: &Path,
    overwrite: bool,
) -> LaunchResult<usize> {
    let mut archive = open_zip(installer)?;
    let mut count = 0;
    for i in 0..archive.len() {
        let mut entry = archive
            .by_index(i)
            .map_err(|e| LaunchError::Parse(e.to_string()))?;
        if entry.is_dir() {
            continue;
        }
        let name = entry.name().to_string();
        let rel = match name.strip_prefix("maven/") {
            Some(r) if is_safe_relative_path(r) => r.to_string(),
            _ => continue,
        };
        let out = libraries_dir.join(&rel);
        if out.exists() && !overwrite {
            continue;
        }
        if let Some(parent) = out.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let mut file = std::fs::File::create(&out)?;
        std::io::copy(&mut entry, &mut file)?;
        count += 1;
    }
    Ok(count)
}

fn extract_single(installer: &Path, entry_name: &str, out: &Path) -> LaunchResult<()> {
    let mut archive = open_zip(installer)?;
    let data = read_zip_entry(&mut archive, entry_name)?
        .ok_or_else(|| LaunchError::NotFound(format!("{} not found in installer", entry_name)))?;
    if let Some(parent) = out.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(out, data)?;
    Ok(())
}

fn jar_main_class(jar: &Path) -> LaunchResult<String> {
    let mut archive = open_zip(jar)?;
    let manifest = read_zip_entry(&mut archive, "META-INF/MANIFEST.MF")?
        .ok_or_else(|| LaunchError::Parse(format!("{} has no manifest", jar.display())))?;
    let text = String::from_utf8_lossy(&manifest);
    let mut lines = text.lines().peekable();
    while let Some(line) = lines.next() {
        if let Some(value) = line.strip_prefix("Main-Class:") {
            let mut value = value.trim().to_string();
            while let Some(next) = lines.peek() {
                if let Some(cont) = next.strip_prefix(' ') {
                    value.push_str(cont.trim_end());
                    lines.next();
                } else {
                    break;
                }
            }
            return Ok(value);
        }
    }
    Err(LaunchError::Parse(format!(
        "{} has no Main-Class",
        jar.display()
    )))
}

fn replace_tokens(value: &str, data: &HashMap<String, String>) -> LaunchResult<String> {
    let mut out = String::with_capacity(value.len());
    let mut rest = value;
    while let Some(start) = rest.find('{') {
        out.push_str(&rest[..start]);
        let after = &rest[start + 1..];
        let end = after
            .find('}')
            .ok_or_else(|| LaunchError::Parse(format!("unterminated token in '{}'", value)))?;
        let key = &after[..end];
        let replacement = data.get(key).ok_or_else(|| {
            LaunchError::Parse(format!("installer references unknown value '{}'", key))
        })?;
        out.push_str(replacement);
        rest = &after[end + 1..];
    }
    out.push_str(rest);
    Ok(out)
}

fn resolve_argument(
    ctx: &ForgeInstallContext<'_>,
    arg: &str,
    data: &HashMap<String, String>,
) -> LaunchResult<String> {
    if arg.starts_with('[') && arg.ends_with(']') {
        return Ok(ctx.library_path(arg)?.display().to_string());
    }
    replace_tokens(arg, data)
}

async fn run_processors(
    ctx: &ForgeInstallContext<'_>,
    artifact: &ForgeArtifact,
    installer: &Path,
    profile: &InstallProfile,
) -> LaunchResult<()> {
    let work_dir = ctx
        .caches_dir
        .join("loaders")
        .join("work")
        .join(artifact.slug());
    tokio::fs::create_dir_all(&work_dir).await?;

    let mut data: HashMap<String, String> = HashMap::new();
    for (key, value) in &profile.data {
        let raw = value.client.as_str();
        let resolved = if raw.starts_with('[') && raw.ends_with(']') {
            ctx.library_path(raw)?.display().to_string()
        } else if raw.starts_with('\'') && raw.ends_with('\'') && raw.len() >= 2 {
            raw[1..raw.len() - 1].to_string()
        } else if let Some(entry) = raw.strip_prefix('/') {
            if !is_safe_relative_path(entry) {
                return Err(LaunchError::Parse(format!(
                    "unsafe installer data path '{}'",
                    raw
                )));
            }
            let out = work_dir.join(entry);
            extract_single(installer, entry, &out)?;
            out.display().to_string()
        } else {
            raw.to_string()
        };
        data.insert(key.clone(), resolved);
    }
    data.insert("SIDE".into(), "client".into());
    data.insert("MINECRAFT_JAR".into(), ctx.client_jar.display().to_string());
    data.insert("MINECRAFT_VERSION".into(), ctx.mc_version.to_string());
    data.insert("ROOT".into(), ctx.caches_dir.display().to_string());
    data.insert("INSTALLER".into(), installer.display().to_string());
    data.insert(
        "LIBRARY_DIR".into(),
        ctx.libraries_dir.display().to_string(),
    );

    let separator = if cfg!(windows) { ";" } else { ":" };
    let processors: Vec<&Processor> = profile
        .processors
        .iter()
        .filter(|p| {
            p.sides
                .as_ref()
                .map(|s| s.iter().any(|x| x == "client"))
                .unwrap_or(true)
        })
        .collect();

    for (index, processor) in processors.iter().enumerate() {
        if *ctx.cancel_rx.borrow() {
            return Err(LaunchError::Cancelled);
        }

        let mut outputs: Vec<(PathBuf, String)> = Vec::new();
        for (key, value) in &processor.outputs {
            let path = PathBuf::from(resolve_argument(ctx, key, &data)?);
            let hash = resolve_argument(ctx, value, &data)?
                .trim_matches('\'')
                .to_lowercase();
            outputs.push((path, hash));
        }

        if !outputs.is_empty() && !ctx.verify {
            let mut all_valid = true;
            for (path, hash) in &outputs {
                if !download::file_is_valid(path, Some(hash), 0, true).await {
                    all_valid = false;
                    break;
                }
            }
            if all_valid {
                continue;
            }
        }

        ctx.stage(format!(
            "Patching Minecraft ({}/{})",
            index + 1,
            processors.len()
        ));

        let jar = ctx.library_path(&processor.jar)?;
        let main_class = jar_main_class(&jar)?;
        let mut classpath = vec![jar.display().to_string()];
        for entry in &processor.classpath {
            classpath.push(ctx.library_path(entry)?.display().to_string());
        }
        let mut args = Vec::with_capacity(processor.args.len());
        for arg in &processor.args {
            args.push(resolve_argument(ctx, arg, &data)?);
        }

        let output = process::tokio_command(ctx.java_path)
            .arg("-cp")
            .arg(classpath.join(separator))
            .arg(&main_class)
            .args(&args)
            .current_dir(&work_dir)
            .stdin(std::process::Stdio::null())
            .output()
            .await
            .map_err(|e| {
                LaunchError::LaunchFailed(format!(
                    "failed to run installer step {}: {}",
                    main_class, e
                ))
            })?;

        if !output.status.success() {
            let mut combined = String::from_utf8_lossy(&output.stdout).to_string();
            combined.push('\n');
            combined.push_str(&String::from_utf8_lossy(&output.stderr));
            let tail: Vec<&str> = combined.lines().filter(|l| !l.trim().is_empty()).collect();
            let start = tail.len().saturating_sub(15);
            for line in &tail[start..] {
                ctx.log(format!("[installer] {}", line));
            }
            return Err(LaunchError::LaunchFailed(format!(
                "installer step {} failed with exit code {:?}",
                main_class,
                output.status.code()
            )));
        }

        for (path, hash) in &outputs {
            if !download::file_is_valid(path, Some(hash), 0, true).await {
                return Err(LaunchError::Integrity(format!(
                    "installer step {} produced an unexpected {}",
                    main_class,
                    path.display()
                )));
            }
        }
    }

    let _ = tokio::fs::remove_dir_all(&work_dir).await;
    Ok(())
}

async fn download_libraries(
    ctx: &ForgeInstallContext<'_>,
    libraries: &[Library],
    label: &str,
) -> LaunchResult<()> {
    let mut tasks = Vec::new();
    for library in libraries {
        let mut lib = library.clone();
        libraries::normalize_library(&mut lib);
        let artifact = match &lib.downloads.artifact {
            Some(a) => a,
            None => continue,
        };
        if artifact.url.is_empty() || !is_safe_relative_path(&artifact.path) {
            continue;
        }
        let path = ctx.libraries_dir.join(&artifact.path);
        let sha1 = Some(artifact.sha1.clone()).filter(|s| !s.is_empty());
        if download::file_is_valid(&path, sha1.as_deref(), artifact.size, ctx.verify).await {
            continue;
        }
        tasks.push(DownloadTask::new(
            artifact.url.clone(),
            path,
            sha1,
            artifact.size,
        ));
    }
    download::download_all(ctx.client, tasks, label, ctx.event_tx, ctx.cancel_rx, 8).await
}

async fn ensure_installer(
    ctx: &ForgeInstallContext<'_>,
    kind: LoaderKind,
    artifact: &ForgeArtifact,
) -> LaunchResult<PathBuf> {
    let dir = ctx.caches_dir.join("installers");
    tokio::fs::create_dir_all(&dir).await?;
    let path = dir.join(format!(
        "{}-{}-installer.jar",
        artifact.artifact, artifact.full_version
    ));
    let usable = path.exists() && !ctx.verify && open_zip(&path).is_ok();
    if !usable {
        ctx.stage(format!(
            "Downloading {} {} installer",
            kind.label(),
            artifact.full_version
        ));
        let task = DownloadTask::new(artifact.installer_url(), path.clone(), None, 0);
        download::download_single(ctx.client, &task, ctx.cancel_rx)
            .await
            .map_err(|err| match err {
                LaunchError::Network(msg) if msg.contains("404") => LaunchError::NotFound(format!(
                    "{} {} does not exist for Minecraft {}",
                    kind.label(),
                    artifact.full_version,
                    ctx.mc_version
                )),
                other => other,
            })?;
        open_zip(&path)?;
    }
    Ok(path)
}

pub async fn install(
    ctx: &ForgeInstallContext<'_>,
    kind: LoaderKind,
    loader_version: &str,
) -> LaunchResult<VersionDetails> {
    let artifact = ForgeArtifact::resolve(kind, ctx.mc_version, loader_version)?;
    let profiles_dir = ctx.caches_dir.join("loaders");
    tokio::fs::create_dir_all(&profiles_dir).await?;
    let profile_path = profiles_dir.join(format!("{}.json", artifact.slug()));
    let marker_path = profiles_dir.join(format!("{}.{}", artifact.slug(), PROFILE_MARKER));

    if !ctx.verify && marker_path.exists() {
        if let Ok(text) = tokio::fs::read_to_string(&profile_path).await {
            if let Ok(details) = serde_json::from_str::<VersionDetails>(&text) {
                if local_libraries_present(ctx, &details) {
                    return Ok(details);
                }
            }
        }
    }
    let _ = tokio::fs::remove_file(&marker_path).await;

    let installer = ensure_installer(ctx, kind, &artifact).await?;
    ctx.stage(format!(
        "Installing {} {}",
        kind.label(),
        artifact.full_version
    ));

    let mut archive = open_zip(&installer)?;
    let profile_bytes = read_zip_entry(&mut archive, "install_profile.json")?
        .ok_or_else(|| LaunchError::Parse("installer has no install_profile.json".to_string()))?;
    let profile: InstallProfile = serde_json::from_slice(&profile_bytes)
        .map_err(|e| LaunchError::Parse(format!("install_profile.json: {}", e)))?;

    let details = if let Some(version_info) = profile.version_info {
        let install = profile.install.ok_or_else(|| {
            LaunchError::Parse("legacy installer is missing install data".to_string())
        })?;
        let target = ctx.library_path(&install.path)?;
        if !is_safe_relative_path(&install.file_path) {
            return Err(LaunchError::Parse(
                "legacy installer has an unsafe file path".to_string(),
            ));
        }
        extract_single(&installer, &install.file_path, &target)?;
        let mut details = version_info;
        for lib in details.libraries.iter_mut() {
            if lib.name == install.path {
                lib.url = None;
                lib.downloads.artifact = Some(crate::models::Artifact {
                    path: maven_path(&install.path).unwrap_or_default(),
                    sha1: String::new(),
                    size: 0,
                    url: String::new(),
                });
            }
        }
        details
    } else {
        let json_name = profile
            .json
            .clone()
            .unwrap_or_else(|| "/version.json".to_string())
            .trim_start_matches('/')
            .to_string();
        let version_bytes = read_zip_entry(&mut archive, &json_name)?
            .ok_or_else(|| LaunchError::Parse(format!("installer has no {}", json_name)))?;
        drop(archive);
        let details: VersionDetails = serde_json::from_slice(&version_bytes)
            .map_err(|e| LaunchError::Parse(format!("{}: {}", json_name, e)))?;

        let extracted = extract_maven_folder(&installer, ctx.libraries_dir, ctx.verify)?;
        if extracted > 0 {
            ctx.log(format!(
                "Unpacked {} bundled {} files",
                extracted,
                kind.label()
            ));
        }

        download_libraries(
            ctx,
            &profile.libraries,
            &format!("Downloading {} installer libraries", kind.label()),
        )
        .await?;
        download_libraries(
            ctx,
            &details.libraries,
            &format!("Downloading {} libraries", kind.label()),
        )
        .await?;
        run_processors(ctx, &artifact, &installer, &profile).await?;
        details
    };

    let serialized =
        serde_json::to_string_pretty(&details).map_err(|e| LaunchError::Parse(e.to_string()))?;
    tokio::fs::write(&profile_path, serialized).await?;
    tokio::fs::write(&marker_path, artifact.full_version.as_bytes()).await?;
    ctx.log(format!(
        "{} {} installed ({} libraries, main class {})",
        kind.label(),
        artifact.full_version,
        details.libraries.len(),
        details.main_class
    ));
    Ok(details)
}

fn local_libraries_present(ctx: &ForgeInstallContext<'_>, details: &VersionDetails) -> bool {
    details.libraries.iter().all(|lib| {
        let mut lib = lib.clone();
        libraries::normalize_library(&mut lib);
        match &lib.downloads.artifact {
            Some(a) if a.url.is_empty() && !a.path.is_empty() => {
                ctx.libraries_dir.join(&a.path).exists()
            }
            _ => true,
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn artifact_resolution() {
        let a = ForgeArtifact::resolve(LoaderKind::Forge, "1.20.1", "47.2.0").unwrap();
        assert_eq!(a.full_version, "1.20.1-47.2.0");
        assert_eq!(
            a.installer_url(),
            "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar"
        );
        let b = ForgeArtifact::resolve(LoaderKind::Forge, "1.20.1", "1.20.1-47.2.0").unwrap();
        assert_eq!(b.full_version, "1.20.1-47.2.0");
        let n = ForgeArtifact::resolve(LoaderKind::NeoForge, "1.21.1", "21.1.77").unwrap();
        assert_eq!(
            n.installer_url(),
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.77/neoforge-21.1.77-installer.jar"
        );
        assert!(ForgeArtifact::resolve(LoaderKind::Forge, "1.20.1", "../../x").is_err());
    }

    #[test]
    fn tokens() {
        let mut data = HashMap::new();
        data.insert("A".to_string(), "x".to_string());
        assert_eq!(replace_tokens("--in={A}/y", &data).unwrap(), "--in=x/y");
        assert!(replace_tokens("{B}", &data).is_err());
    }

    #[test]
    fn neoforge_prefixes() {
        assert_eq!(neoforge_prefix("1.21").unwrap(), "21.0.");
        assert_eq!(neoforge_prefix("1.21.1").unwrap(), "21.1.");
        assert_eq!(neoforge_prefix("1.20.4").unwrap(), "20.4.");
    }

    #[test]
    fn xml_versions() {
        let xml = "<metadata><versioning><versions><version>1.20.1-47.2.0</version><version>1.20.1-47.1.0</version></versions></versioning></metadata>";
        assert_eq!(
            extract_xml_versions(xml),
            vec!["1.20.1-47.2.0", "1.20.1-47.1.0"]
        );
    }
}
