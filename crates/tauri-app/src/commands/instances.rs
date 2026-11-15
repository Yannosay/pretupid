use pretupid_core::instance::{
    is_safe_file_name, CreateInstanceRequest, Instance, InstanceSettings, ModLoader,
    NewInstanceMod, UpdateInstanceRequest,
};
use serde::Serialize;
use tauri::State;
use uuid::Uuid;

use super::content::{content_dir, content_file_path, load_instance};
use super::util::{self, ExpectedHash};
use crate::state::AppState;

const OPENABLE_FOLDERS: &[&str] = &[
    "mods",
    "resourcepacks",
    "shaderpacks",
    "saves",
    "screenshots",
    "logs",
    "crash-reports",
    "config",
];

fn validate_name(name: &str) -> Result<String, String> {
    let trimmed = name.trim();
    if trimmed.is_empty() {
        return Err("Instance name cannot be empty.".to_string());
    }
    if trimmed.chars().count() > 64 {
        return Err("Instance name must be 64 characters or fewer.".to_string());
    }
    Ok(trimmed.to_string())
}

const MAX_ICON_DATA_URL_BYTES: usize = 512 * 1024;
const ICON_DATA_PREFIXES: &[&str] = &[
    "data:image/png;base64,",
    "data:image/jpeg;base64,",
    "data:image/webp;base64,",
    "data:image/gif;base64,",
];
const ICON_STYLE_KINDS: &[&str] = &["block"];

fn validate_icon_url(icon: &str) -> Result<(), String> {
    if icon.is_empty() || util::is_allowed_download_url(icon) {
        return Ok(());
    }
    if !ICON_DATA_PREFIXES.iter().any(|p| icon.starts_with(p)) {
        return Err("Icons must be PNG, JPEG, WebP or GIF images.".to_string());
    }
    if icon.len() > MAX_ICON_DATA_URL_BYTES {
        return Err("That image is too large. Pick one under 384 KB.".to_string());
    }
    let payload = &icon[icon.find(',').map(|i| i + 1).unwrap_or(icon.len())..];
    if !payload
        .bytes()
        .all(|b| b.is_ascii_alphanumeric() || b == b'+' || b == b'/' || b == b'=')
    {
        return Err("The image data is not valid.".to_string());
    }
    Ok(())
}

fn validate_icon_style(style: &str) -> Result<(), String> {
    if style.is_empty() {
        return Ok(());
    }
    let mut parts = style.split(':');
    let kind = parts.next().unwrap_or_default();
    let value = parts.next().unwrap_or_default();
    let valid_value = !value.is_empty()
        && value.len() <= 24
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-');
    if ICON_STYLE_KINDS.contains(&kind) && valid_value && parts.next().is_none() {
        Ok(())
    } else {
        Err("Unknown icon style.".to_string())
    }
}

fn validate_version(version: &str) -> Result<(), String> {
    if mc_launch::VersionResolver::is_valid_version_id(version.trim()) {
        Ok(())
    } else {
        Err("Minecraft version must be selected.".to_string())
    }
}

#[tauri::command]
pub async fn create_instance(
    state: State<'_, AppState>,
    name: String,
    description: String,
    minecraft_version: String,
    mod_loader: ModLoader,
    loader_version: Option<String>,
) -> Result<Instance, String> {
    let name = validate_name(&name)?;
    validate_version(&minecraft_version)?;
    let loader_version = loader_version
        .map(|v| v.trim().to_string())
        .filter(|v| !v.is_empty() && v != "latest");
    if matches!(mod_loader, ModLoader::Forge | ModLoader::NeoForge) && loader_version.is_none() {
        return Err("Pick a loader version for Forge or NeoForge.".to_string());
    }

    let instance = state
        .instances
        .create(CreateInstanceRequest {
            name,
            description: description.trim().chars().take(500).collect(),
            minecraft_version: minecraft_version.trim().to_string(),
            mod_loader,
            loader_version,
        })
        .await
        .map_err(|e| e.to_string())?;

    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    std::fs::create_dir_all(&profile_dir).map_err(|e| e.to_string())?;
    if instance.mod_loader != ModLoader::Vanilla {
        std::fs::create_dir_all(profile_dir.join("mods")).map_err(|e| e.to_string())?;
    }
    Ok(instance)
}

#[tauri::command]
pub async fn list_instances(state: State<'_, AppState>) -> Result<Vec<Instance>, String> {
    state.instances.list().await.map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_instance(
    state: State<'_, AppState>,
    id: String,
) -> Result<Option<Instance>, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .get_by_id(uuid)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn update_instance(
    state: State<'_, AppState>,
    id: String,
    mut request: UpdateInstanceRequest,
) -> Result<Option<Instance>, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    if let Some(name) = &request.name {
        request.name = Some(validate_name(name)?);
    }
    if let Some(version) = &request.minecraft_version {
        validate_version(version)?;
    }
    if let Some(icon) = &request.icon_url {
        validate_icon_url(icon)?;
    }
    if let Some(style) = &request.icon_style {
        validate_icon_style(style)?;
    }
    if let Some(group) = &request.group_name {
        request.group_name = Some(group.trim().chars().take(32).collect());
    }
    state
        .instances
        .update(uuid, request)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn delete_instance(state: State<'_, AppState>, id: String) -> Result<bool, String> {
    let instance = load_instance(&state, &id).await?;
    {
        let running = state
            .cancel_channels
            .lock()
            .map_err(|_| "state lock poisoned".to_string())?;
        if running.contains_key(&id) {
            return Err("Stop the game before deleting this instance.".to_string());
        }
    }
    state
        .instances
        .delete(instance.id)
        .await
        .map_err(|e| e.to_string())?;
    if !is_safe_file_name(&instance.folder_name) {
        return Ok(true);
    }
    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    if profile_dir.exists()
        && profile_dir.starts_with(&state.profiles_dir)
        && profile_dir != state.profiles_dir
    {
        tokio::fs::remove_dir_all(&profile_dir)
            .await
            .map_err(|e| e.to_string())?;
    }
    Ok(true)
}

#[tauri::command]
pub async fn duplicate_instance(
    state: State<'_, AppState>,
    id: String,
    name: Option<String>,
) -> Result<Instance, String> {
    let source = load_instance(&state, &id).await?;
    let new_name = validate_name(&name.unwrap_or_else(|| format!("{} (copy)", source.name)))?;
    let created = state
        .instances
        .create(CreateInstanceRequest {
            name: new_name,
            description: source.description.clone(),
            minecraft_version: source.minecraft_version.clone(),
            mod_loader: source.mod_loader.clone(),
            loader_version: source.loader_version.clone(),
        })
        .await
        .map_err(|e| e.to_string())?;

    let update = UpdateInstanceRequest {
        icon_url: source.icon_url.clone(),
        group_name: source.group_name.clone(),
        icon_style: source.icon_style.clone(),
        ..Default::default()
    };
    let created = state
        .instances
        .update(created.id, update)
        .await
        .map_err(|e| e.to_string())?
        .unwrap_or(created);

    let settings = state
        .instances
        .get_settings(source.id)
        .await
        .map_err(|e| e.to_string())?;
    state
        .instances
        .save_settings(created.id, &settings)
        .await
        .map_err(|e| e.to_string())?;

    let from = state.profiles_dir.join(&source.folder_name);
    let to = state.profiles_dir.join(&created.folder_name);
    if from.exists() {
        tokio::task::spawn_blocking(move || {
            util::copy_dir_recursive(&from, &to, &["logs", "crash-reports"])
        })
        .await
        .map_err(|e| e.to_string())??;
    }

    for entry in state
        .instances
        .list_mods(&source.id.to_string())
        .await
        .map_err(|e| e.to_string())?
    {
        let record = NewInstanceMod {
            instance_id: created.id.to_string(),
            project_id: entry.project_id,
            project_type: entry.project_type,
            title: entry.title,
            icon_url: entry.icon_url,
            version_id: entry.version_id,
            version_name: entry.version_name,
            file_url: entry.file_url,
            file_name: entry.file_name,
            sha1: entry.sha1,
            enabled: entry.enabled,
        };
        state
            .instances
            .add_mod(&record)
            .await
            .map_err(|e| e.to_string())?;
    }
    Ok(created)
}

#[tauri::command]
pub async fn save_instance_settings(
    state: State<'_, AppState>,
    id: String,
    mut settings: InstanceSettings,
) -> Result<(), String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    settings.memory_max_mb = settings.memory_max_mb.clamp(512, 262_144);
    settings.memory_min_mb = settings.memory_min_mb.clamp(256, settings.memory_max_mb);
    settings.resolution_width = settings.resolution_width.clamp(320, 16_384);
    settings.resolution_height = settings.resolution_height.clamp(240, 16_384);
    settings.extra_jvm_args.retain(|a| !a.trim().is_empty());
    settings.extra_game_args.retain(|a| !a.trim().is_empty());
    state
        .instances
        .save_settings(uuid, &settings)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_instance_settings(
    state: State<'_, AppState>,
    id: String,
) -> Result<InstanceSettings, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .get_settings(uuid)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn open_instance_folder(
    state: State<'_, AppState>,
    id: String,
    folder: Option<String>,
) -> Result<(), String> {
    let instance = load_instance(&state, &id).await?;
    let mut path = state.profiles_dir.join(&instance.folder_name);
    if let Some(sub) = folder.as_deref().filter(|s| !s.is_empty()) {
        if !OPENABLE_FOLDERS.contains(&sub) {
            return Err("That folder cannot be opened.".to_string());
        }
        path = path.join(sub);
    }
    util::open_in_file_manager(&path)
}

#[derive(Debug, Serialize)]
pub struct RepairFailure {
    pub title: String,
    pub file_name: String,
    pub reason: String,
}

#[derive(Debug, Serialize)]
pub struct RepairReport {
    pub total: usize,
    pub healthy: usize,
    pub repaired: usize,
    pub failed: Vec<RepairFailure>,
}

#[tauri::command]
pub async fn repair_instance_mods(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<RepairReport, String> {
    let instance = load_instance(&state, &instance_id).await?;
    let entries = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;
    let client = util::http_client()?;
    let mut report = RepairReport {
        total: 0,
        healthy: 0,
        repaired: 0,
        failed: Vec::new(),
    };

    for entry in entries {
        let path = match content_file_path(&state, &instance, &entry) {
            Some(p) => p,
            None => continue,
        };
        report.total += 1;
        let file_name = entry.file_name.clone().unwrap_or_default();
        let current = if path.exists() {
            util::sha1_file(&path).await.ok()
        } else {
            None
        };
        let healthy = match (&current, &entry.sha1) {
            (Some(actual), Some(expected)) => actual.eq_ignore_ascii_case(expected),
            (Some(_), None) => true,
            (None, _) => false,
        };
        if healthy {
            report.healthy += 1;
            continue;
        }
        let url = match entry.file_url.as_deref().filter(|u| !u.is_empty()) {
            Some(u) => u.to_string(),
            None => {
                report.failed.push(RepairFailure {
                    title: entry.title.clone(),
                    file_name,
                    reason: "This file was added manually and has no download source.".to_string(),
                });
                continue;
            }
        };
        if content_dir(&state, &instance, &entry.project_type).is_err() {
            continue;
        }
        let expected = ExpectedHash {
            sha1: entry.sha1.as_deref(),
            sha512: None,
        };
        match util::download_verified(&client, &url, &path, &expected).await {
            Ok(_) => report.repaired += 1,
            Err(reason) => report.failed.push(RepairFailure {
                title: entry.title.clone(),
                file_name,
                reason,
            }),
        }
    }
    Ok(report)
}

#[derive(Debug, Serialize)]
pub struct SystemInfo {
    pub total_memory_mb: u64,
    pub recommended_memory_mb: u64,
    pub cpu_threads: usize,
    pub os: String,
}

#[tauri::command]
pub fn get_system_info() -> SystemInfo {
    let mut system = sysinfo::System::new();
    system.refresh_memory();
    let total_memory_mb = system.total_memory() / 1024 / 1024;
    let recommended = match total_memory_mb {
        0..=4095 => 1536,
        4096..=8191 => 3072,
        8192..=16383 => 4096,
        16384..=32767 => 6144,
        _ => 8192,
    };
    SystemInfo {
        total_memory_mb,
        recommended_memory_mb: recommended,
        cpu_threads: std::thread::available_parallelism()
            .map(|n| n.get())
            .unwrap_or(1),
        os: std::env::consts::OS.to_string(),
    }
}
