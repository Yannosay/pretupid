use pretupid_core::instance::{
    CreateInstanceRequest, Instance, InstanceMod, InstanceRepository, InstanceSettings, ModLoader,
    UpdateInstanceRequest,
};
use sqlx::SqlitePool;
use std::collections::HashMap;
use std::path::PathBuf;
use tauri::{AppHandle, Emitter, State};
use uuid::Uuid;

use crate::state::AppState;
use modrinth_client::models::{Project, Version, VersionType};
use modrinth_client::ModrinthClient;
use tokio::io::AsyncWriteExt;

const USER_AGENT: &str = concat!(
    "pretupid-launcher/",
    env!("CARGO_PKG_VERSION"),
    " (contact: yannosay)"
);

fn build_http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .timeout(std::time::Duration::from_secs(300))
        .build()
        .map_err(|e| e.to_string())
}

async fn download_to_file(
    client: &reqwest::Client,
    url: &str,
    path: &std::path::Path,
) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }

    let response = client.get(url).send().await.map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        return Err(format!(
            "Download failed with status {} for {}",
            response.status(),
            url
        ));
    }

    let mut file = tokio::fs::File::create(path)
        .await
        .map_err(|e| e.to_string())?;
    let mut stream = response.bytes_stream();
    use futures_util::StreamExt;
    while let Some(chunk) = stream.next().await {
        let bytes = chunk.map_err(|e| e.to_string())?;
        file.write_all(&bytes).await.map_err(|e| e.to_string())?;
    }
    file.flush().await.map_err(|e| e.to_string())?;
    Ok(())
}

fn copy_dir_recursive(src: &std::path::Path, dst: &std::path::Path) -> Result<(), String> {
    for entry in std::fs::read_dir(src).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let entry_type = entry.file_type().map_err(|e| e.to_string())?;
        let target = dst.join(entry.file_name());

        if entry_type.is_dir() {
            std::fs::create_dir_all(&target).map_err(|e| e.to_string())?;
            copy_dir_recursive(&entry.path(), &target)?;
        } else {
            if let Some(parent) = target.parent() {
                std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
            }
            std::fs::copy(entry.path(), &target).map_err(|e| e.to_string())?;
        }
    }

    Ok(())
}

fn extract_zip(zip_path: &std::path::Path, dest: &std::path::Path) -> Result<(), String> {
    let file = std::fs::File::open(zip_path).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
        let out_path = match entry.enclosed_name() {
            Some(p) => dest.join(p),
            None => continue,
        };
        if entry.is_dir() {
            std::fs::create_dir_all(&out_path).map_err(|e| e.to_string())?;
        } else {
            if let Some(parent) = out_path.parent() {
                std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
            }
            let mut out_file = std::fs::File::create(&out_path).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out_file).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

fn emit_progress(app: &AppHandle, instance_id: &str, message: &str) {
    let _ = app.emit_to(
        "main",
        "modpack://progress",
        serde_json::json!({
            "instance_id": instance_id,
            "message": message
        }),
    );
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
    if name.trim().is_empty() {
        return Err("Instance name cannot be empty.".to_string());
    }
    if minecraft_version.trim().is_empty() {
        return Err("Minecraft version must be selected.".to_string());
    }

    let request = CreateInstanceRequest {
        name,
        description,
        minecraft_version,
        mod_loader,
        loader_version,
    };

    let instance = state
        .instances
        .create(request)
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
    request: UpdateInstanceRequest,
) -> Result<Option<Instance>, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .update(uuid, request)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn delete_instance(state: State<'_, AppState>, id: String) -> Result<bool, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    let instance = state
        .instances
        .get_by_id(uuid)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Instance not found".to_string())?;

    state
        .instances
        .delete(uuid)
        .await
        .map_err(|e| e.to_string())?;

    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    if profile_dir.exists() {
        std::fs::remove_dir_all(&profile_dir).map_err(|e| e.to_string())?;
    }

    Ok(true)
}

#[tauri::command]
pub async fn save_instance_settings(
    state: State<'_, AppState>,
    id: String,
    settings: InstanceSettings,
) -> Result<(), String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
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
pub async fn list_instance_mods(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<InstanceMod>, String> {
    state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn add_instance_mod(
    state: State<'_, AppState>,
    instance_id: String,
    project_id: String,
    project_type: String,
    title: String,
    icon_url: Option<String>,
    version_id: Option<String>,
    version_name: Option<String>,
    file_url: Option<String>,
    file_name: Option<String>,
) -> Result<InstanceMod, String> {
    let instance_uuid = Uuid::parse_str(&instance_id).map_err(|e| e.to_string())?;
    let instance = state
        .instances
        .get_by_id(instance_uuid)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Instance not found".to_string())?;

    if instance.mod_loader == ModLoader::Vanilla {
        return Err(
            "Vanilla instances do not accept mods. Create a Fabric, Quilt, Forge, or NeoForge instance first."
                .to_string(),
        );
    }

    let existing_mods = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;
    if existing_mods.iter().any(|m| m.project_id == project_id) {
        return Err("This mod is already installed in this instance.".to_string());
    }

    let versions = state
        .modrinth
        .list_versions(&project_id)
        .await
        .map_err(|e| format!("failed to fetch mod versions: {e}"))?;

    let selected_version = if let Some(vid) = version_id.clone() {
        versions
            .iter()
            .find(|v| v.id == vid)
            .ok_or_else(|| "Selected mod version not found.".to_string())?
    } else if file_url.is_none() {
        versions
            .iter()
            .find(|v| v.version_type == VersionType::Release)
            .or_else(|| versions.first())
            .ok_or_else(|| "No mod version available.".to_string())?
    } else {
        versions
            .iter()
            .find(|v| {
                Some(v.name.as_str()) == version_name.as_deref()
                    || v.files.iter().any(|f| {
                        Some(f.url.as_str()) == file_url.as_deref()
                            || Some(f.filename.as_str()) == file_name.as_deref()
                    })
            })
            .ok_or_else(|| {
                "Provided mod file does not match any available version.".to_string()
            })?
    };

    let game_compatible = selected_version
        .game_versions
        .iter()
        .any(|gv| gv == &instance.minecraft_version);
    if !game_compatible {
        return Err(format!(
            "This mod version does not support Minecraft {}",
            instance.minecraft_version
        ));
    }

    let instance_loader = match instance.mod_loader {
        ModLoader::Modrinth => "fabric",
        ModLoader::Vanilla => "vanilla",
        ModLoader::Fabric => "fabric",
        ModLoader::Quilt => "quilt",
        ModLoader::Forge => "forge",
        ModLoader::NeoForge => "neoforge",
    };

    let loader_compatible = selected_version
        .loaders
        .iter()
        .any(|l| l == instance_loader);
    if !loader_compatible {
        return Err(format!(
            "This mod version does not support the {} loader",
            instance_loader
        ));
    }

    let file = selected_version
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| selected_version.files.first())
        .ok_or_else(|| "Selected mod version has no downloadable files.".to_string())?;

    let resolved_version_id = selected_version.id.clone();
    let resolved_version_name = selected_version.name.clone();
    let resolved_file_url = file.url.clone();
    let resolved_file_name = file.filename.clone();

    let mod_record = state
        .instances
        .add_mod(
            &instance_id,
            &project_id,
            &project_type,
            &title,
            icon_url.as_deref(),
            Some(&resolved_version_id),
            Some(&resolved_version_name),
            Some(&resolved_file_url),
            Some(&resolved_file_name),
        )
        .await
        .map_err(|e| e.to_string())?;

    let client = build_http_client()?;
    let mods_dir = state.profiles_dir.join(&instance.folder_name).join("mods");
    std::fs::create_dir_all(&mods_dir).map_err(|e| e.to_string())?;
    let dest = mods_dir.join(&resolved_file_name);

    if let Err(err) = download_to_file(&client, &resolved_file_url, &dest).await {
        let _ = state.instances.remove_mod(&mod_record.id).await;
        return Err(format!("Failed to download mod file: {err}"));
    }

    Ok(mod_record)
}

#[tauri::command]
pub async fn remove_instance_mod(
    state: State<'_, AppState>,
    mod_id: String,
) -> Result<bool, String> {
    let mod_info = state
        .instances
        .get_mod_by_id(&mod_id)
        .await
        .map_err(|e| e.to_string())?;

    let removed = state
        .instances
        .remove_mod(&mod_id)
        .await
        .map_err(|e| e.to_string())?;

    if removed {
        if let Some(m) = mod_info {
            if let Some(file_name) = m.file_name {
                if let Ok(instance_uuid) = Uuid::parse_str(&m.instance_id) {
                    if let Ok(Some(instance)) = state.instances.get_by_id(instance_uuid).await {
                        let file_path = state
                            .profiles_dir
                            .join(&instance.folder_name)
                            .join("mods")
                            .join(file_name);
                        if file_path.exists() {
                            let _ = tokio::fs::remove_file(file_path).await;
                        }
                    }
                }
            }
        }
    }

    Ok(removed)
}

struct ModpackIndex {
    mc_version: String,
    loader: Option<ModLoader>,
    loader_version: Option<String>,
    files: Vec<serde_json::Value>,
}

fn parse_modpack_index(index_json: &serde_json::Value) -> Result<ModpackIndex, String> {
    let dependencies = index_json
        .get("dependencies")
        .and_then(|d| d.as_object())
        .cloned()
        .unwrap_or_default();

    let mc_version = dependencies
        .get("minecraft")
        .and_then(|v| v.as_str())
        .map(String::from)
        .or_else(|| {
            index_json
                .get("versionId")
                .and_then(|v| v.as_str())
                .map(String::from)
        })
        .ok_or_else(|| "modpack index missing Minecraft version".to_string())?;

    let (loader, loader_version) = if dependencies.contains_key("fabric-loader") {
        (
            Some(ModLoader::Fabric),
            dependencies
                .get("fabric-loader")
                .and_then(|v| v.as_str())
                .map(String::from),
        )
    } else if dependencies.contains_key("quilt-loader") {
        (
            Some(ModLoader::Quilt),
            dependencies
                .get("quilt-loader")
                .and_then(|v| v.as_str())
                .map(String::from),
        )
    } else if dependencies.contains_key("forge") {
        (
            Some(ModLoader::Forge),
            dependencies
                .get("forge")
                .and_then(|v| v.as_str())
                .map(String::from),
        )
    } else if dependencies.contains_key("neoforge") {
        (
            Some(ModLoader::NeoForge),
            dependencies
                .get("neoforge")
                .and_then(|v| v.as_str())
                .map(String::from),
        )
    } else {
        (None, None)
    };

    let files = index_json
        .get("files")
        .and_then(|f| f.as_array())
        .cloned()
        .unwrap_or_default();

    Ok(ModpackIndex {
        mc_version,
        loader,
        loader_version,
        files,
    })
}

async fn resolve_mod_metadata(
    modrinth: &ModrinthClient,
    hashes: &[String],
) -> (HashMap<String, Version>, HashMap<String, Project>) {
    if hashes.is_empty() {
        return (HashMap::new(), HashMap::new());
    }

    let versions = match modrinth.get_versions_from_hashes(hashes).await {
        Ok(v) => v,
        Err(err) => {
            tracing::warn!("Modrinth version_files lookup failed: {err}");
            HashMap::new()
        }
    };

    let mut project_ids: Vec<String> = Vec::new();
    for version in versions.values() {
        if !project_ids.contains(&version.project_id) {
            project_ids.push(version.project_id.clone());
        }
    }

    let projects = if project_ids.is_empty() {
        HashMap::new()
    } else {
        match modrinth.get_projects_by_ids(&project_ids).await {
            Ok(list) => {
                let mut map = HashMap::new();
                for project in list {
                    map.insert(project.id.clone(), project);
                }
                map
            }
            Err(err) => {
                tracing::warn!("Modrinth projects lookup failed: {err}");
                HashMap::new()
            }
        }
    };

    (versions, projects)
}

async fn run_modpack_install(
    app: AppHandle,
    pool: SqlitePool,
    profiles_dir: PathBuf,
    caches_dir: PathBuf,
    instance: Instance,
    modpack_url: String,
) -> Result<(), String> {
    let instance_id_str = instance.id.to_string();
    let instances_repo = InstanceRepository::new(pool);
    let profile_dir = profiles_dir.join(&instance.folder_name);

    emit_progress(&app, &instance_id_str, "Downloading modpack archive");

    let temp_dir = caches_dir.join("tmp").join(Uuid::new_v4().to_string());
    tokio::fs::create_dir_all(&temp_dir)
        .await
        .map_err(|e| e.to_string())?;

    let client = build_http_client()?;

    let zip_path = temp_dir.join("modpack.zip");
    download_to_file(&client, &modpack_url, &zip_path).await?;

    emit_progress(&app, &instance_id_str, "Extracting modpack archive");

    let extract_dir = temp_dir.join("extract");
    tokio::fs::create_dir_all(&extract_dir)
        .await
        .map_err(|e| e.to_string())?;
    extract_zip(&zip_path, &extract_dir)?;

    let index_path = extract_dir.join("modrinth.index.json");
    if !index_path.exists() {
        let _ = tokio::fs::remove_dir_all(&temp_dir).await;
        return Err("modrinth.index.json not found in modpack archive.".to_string());
    }

    let index_content = tokio::fs::read_to_string(&index_path)
        .await
        .map_err(|e| e.to_string())?;
    let index_json: serde_json::Value =
        serde_json::from_str(&index_content).map_err(|e| e.to_string())?;

    let index = parse_modpack_index(&index_json)?;

    let loader_differs = match index.loader.as_ref() {
        Some(loader) => *loader != instance.mod_loader,
        None => false,
    };

    let loader_version_differs = match index.loader_version.as_ref() {
        Some(version) => Some(version) != instance.loader_version.as_ref(),
        None => false,
    };

    let needs_update = index.mc_version != instance.minecraft_version
        || loader_differs
        || loader_version_differs;

    if needs_update {
        let update_req = UpdateInstanceRequest {
            name: None,
            description: None,
            minecraft_version: Some(index.mc_version.clone()),
            mod_loader: index.loader.clone(),
            loader_version: index.loader_version.clone(),
        };
        let _ = instances_repo.update(instance.id, update_req).await;
        let _ = app.emit_to(
            "main",
            "modpack://instance-updated",
            serde_json::json!({
                "instance_id": instance_id_str,
                "minecraft_version": index.mc_version,
            }),
        );
    }

    let mut mod_entries: Vec<(String, String, String)> = Vec::new();
    let mut non_mod_entries: Vec<(String, String)> = Vec::new();
    let mut hashes_for_lookup: Vec<String> = Vec::new();

    for file in &index.files {
        if let Some(env) = file.get("env").and_then(|e| e.as_object()) {
            if env.get("client").and_then(|v| v.as_str()) == Some("unsupported") {
                continue;
            }
        }

        let path = match file.get("path").and_then(|p| p.as_str()) {
            Some(p) => p.to_string(),
            None => continue,
        };
        let downloads = match file.get("downloads").and_then(|d| d.as_array()) {
            Some(d) => d,
            None => continue,
        };
        let url = match downloads.first().and_then(|u| u.as_str()) {
            Some(u) => u.to_string(),
            None => continue,
        };

        if path.starts_with("mods/") {
            let file_name = std::path::Path::new(&path)
                .file_name()
                .and_then(|f| f.to_str())
                .unwrap_or(&path)
                .to_string();
            let sha1 = file
                .get("hashes")
                .and_then(|h| h.get("sha1"))
                .and_then(|s| s.as_str())
                .unwrap_or("")
                .to_string();
            if !sha1.is_empty() {
                hashes_for_lookup.push(sha1.clone());
            }
            mod_entries.push((sha1, url, file_name));
        } else {
            non_mod_entries.push((path, url));
        }
    }

    emit_progress(
        &app,
        &instance_id_str,
        "Resolving mod metadata from Modrinth",
    );

    let modrinth = ModrinthClient::new().map_err(|e| e.to_string())?;
    let (version_map, project_map) = resolve_mod_metadata(&modrinth, &hashes_for_lookup).await;

    tracing::info!(
        "Resolved {} versions and {} projects for modpack {}",
        version_map.len(),
        project_map.len(),
        instance.name
    );

    let total_files = (mod_entries.len() + non_mod_entries.len()).max(1);
    let mut downloaded = 0usize;

    for (path, url) in &non_mod_entries {
        let dest = profile_dir.join(path);
        download_to_file(&client, url, &dest).await?;
        downloaded += 1;
        emit_progress(
            &app,
            &instance_id_str,
            &format!("Installing files ({downloaded}/{total_files})"),
        );
    }

    let mut mods_with_icons = 0usize;

    for (sha1, url, file_name) in &mod_entries {
        let dest = profile_dir.join("mods").join(file_name);
        download_to_file(&client, url, &dest).await?;
        downloaded += 1;
        emit_progress(
            &app,
            &instance_id_str,
            &format!("Downloading mods ({downloaded}/{total_files})"),
        );

        let version = version_map.get(sha1);
        let version_id = version.map(|v| v.id.clone());
        let version_name = version.map(|v| v.name.clone());

        let project_id = version
            .map(|v| v.project_id.clone())
            .unwrap_or_else(|| sha1.clone());

        let (title, icon_url) = match (version, project_map.get(&project_id)) {
            (Some(_), Some(project)) => {
                if project.icon_url.is_some() {
                    mods_with_icons += 1;
                }
                (project.title.clone(), project.icon_url.clone())
            }
            (None, Some(project)) => {
                if project.icon_url.is_some() {
                    mods_with_icons += 1;
                }
                (project.title.clone(), project.icon_url.clone())
            }
            _ => (
                file_name
                    .trim_end_matches(".jar")
                    .trim_end_matches(".disabled")
                    .to_string(),
                None,
            ),
        };

        let _ = instances_repo
            .add_mod(
                &instance_id_str,
                &project_id,
                "mod",
                &title,
                icon_url.as_deref(),
                version_id.as_deref(),
                version_name.as_deref(),
                Some(url),
                Some(file_name),
            )
            .await;
    }

    tracing::info!(
        "Modpack {} installed: {} mods, {} with icons",
        instance.name,
        mod_entries.len(),
        mods_with_icons
    );

    emit_progress(&app, &instance_id_str, "Copying overrides");

    let overrides_dir = extract_dir.join("overrides");
    if overrides_dir.exists() {
        copy_dir_recursive(&overrides_dir, &profile_dir)?;
    }

    let client_overrides = extract_dir.join("client-overrides");
    if client_overrides.exists() {
        copy_dir_recursive(&client_overrides, &profile_dir)?;
    }

    let _ = tokio::fs::remove_dir_all(&temp_dir).await;

    let _ = app.emit_to(
        "main",
        "modpack://complete",
        serde_json::json!({
            "instance_id": instance_id_str,
            "name": instance.name
        }),
    );

    Ok(())
}

#[tauri::command]
pub async fn create_instance_from_modpack(
    app: AppHandle,
    state: State<'_, AppState>,
    project_id: String,
    version_id: Option<String>,
    name: String,
) -> Result<Instance, String> {
    if name.trim().is_empty() {
        return Err("Instance name cannot be empty.".to_string());
    }

    let project = state
        .modrinth
        .get_project(&project_id)
        .await
        .map_err(|e| format!("failed to fetch project: {e}"))?;

    if project.project_type != modrinth_client::models::ProjectType::Modpack {
        return Err("Selected project is not a modpack".to_string());
    }

    let versions = state
        .modrinth
        .list_versions(&project_id)
        .await
        .map_err(|e| format!("failed to fetch modpack versions: {e}"))?;

    let selected_version = if let Some(vid) = version_id {
        versions
            .iter()
            .find(|v| v.id == vid)
            .ok_or_else(|| "Selected modpack version not found".to_string())?
    } else {
        versions
            .iter()
            .find(|v| v.version_type == VersionType::Release)
            .or_else(|| versions.first())
            .ok_or_else(|| "No modpack version available".to_string())?
    };

    let primary_file = selected_version
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| selected_version.files.first())
        .ok_or_else(|| "No modpack file available".to_string())?;

    let modpack_url = primary_file.url.clone();

    let mc_version = extract_mc_version_from_version(selected_version);
    let loader_enum = mod_loader_from_loaders(&selected_version.loaders);

    let request = CreateInstanceRequest {
        name: name.clone(),
        description: project.description.clone(),
        minecraft_version: mc_version,
        mod_loader: loader_enum,
        loader_version: None,
    };

    let instance = state
        .instances
        .create(request)
        .await
        .map_err(|e| e.to_string())?;

    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    std::fs::create_dir_all(&profile_dir).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(profile_dir.join("mods")).map_err(|e| e.to_string())?;

    let app_for_bg = app.clone();
    let pool_for_bg = state.pool.clone();
    let profiles_for_bg = state.profiles_dir.clone();
    let caches_for_bg = state.caches_dir.clone();
    let instance_for_bg = instance.clone();
    let instance_id_str = instance.id.to_string();

    tauri::async_runtime::spawn(async move {
        if let Err(err) = run_modpack_install(
            app_for_bg.clone(),
            pool_for_bg.clone(),
            profiles_for_bg.clone(),
            caches_for_bg,
            instance_for_bg.clone(),
            modpack_url,
        )
        .await
        {
            let _ = app_for_bg.emit_to(
                "main",
                "modpack://error",
                serde_json::json!({
                    "instance_id": instance_id_str,
                    "message": err
                }),
            );

            let repo = InstanceRepository::new(pool_for_bg);
            let _ = repo.delete(instance_for_bg.id).await;
            let _ = std::fs::remove_dir_all(profiles_for_bg.join(&instance_for_bg.folder_name));
        }
    });

    Ok(instance)
}

fn extract_mc_version_from_version(version: &Version) -> String {
    version
        .game_versions
        .iter()
        .find(|v| v.chars().next().map(|c| c.is_ascii_digit()).unwrap_or(false))
        .cloned()
        .unwrap_or_else(|| "1.20.1".to_string())
}

fn mod_loader_from_loaders(loaders: &[String]) -> ModLoader {
    for loader in loaders {
        match loader.as_str() {
            "fabric" => return ModLoader::Fabric,
            "quilt" => return ModLoader::Quilt,
            "forge" => return ModLoader::Forge,
            "neoforge" => return ModLoader::NeoForge,
            _ => {}
        }
    }
    ModLoader::Vanilla
}