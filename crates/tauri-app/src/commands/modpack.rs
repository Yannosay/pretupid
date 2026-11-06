use std::collections::{HashMap, HashSet};
use std::io::Write;
use std::path::{Path, PathBuf};

use modrinth_client::models::{Project, ProjectType, Version, VersionType};
use pretupid_core::instance::{
    content_folder, is_safe_file_name, CreateInstanceRequest, Instance, InstanceRepository,
    ModLoader, NewInstanceMod, UpdateInstanceRequest,
};
use serde::{Deserialize, Serialize};
use sqlx::SqlitePool;
use tauri::{AppHandle, Emitter, Manager, State};
use uuid::Uuid;

use super::content::load_instance;
use super::util::{self, ExpectedHash};
use crate::state::AppState;

const MAX_PACK_BYTES: u64 = 4 * 1024 * 1024 * 1024;
const INDEX_FILE: &str = "modrinth.index.json";
const META_DIR: &str = ".pretupid";

#[derive(Debug, Deserialize, Serialize, Clone)]
struct PackIndex {
    #[serde(rename = "formatVersion", default)]
    format_version: u32,
    #[serde(default)]
    game: String,
    #[serde(rename = "versionId", default)]
    version_id: String,
    #[serde(default)]
    name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    summary: Option<String>,
    #[serde(default)]
    files: Vec<PackFile>,
    #[serde(default)]
    dependencies: HashMap<String, String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
struct PackFile {
    path: String,
    #[serde(default)]
    hashes: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    env: Option<HashMap<String, String>>,
    #[serde(default)]
    downloads: Vec<String>,
    #[serde(rename = "fileSize", default)]
    file_size: u64,
}

enum PackSource {
    Remote(String),
    Local(PathBuf),
}

fn emit_progress(app: &AppHandle, instance_id: &str, status: &str, message: &str) {
    let _ = app.emit_to(
        "main",
        "modpack://progress",
        serde_json::json!({ "instance_id": instance_id, "status": status, "message": message }),
    );
}

fn loader_from_dependencies(deps: &HashMap<String, String>) -> (ModLoader, Option<String>) {
    for (key, loader) in [
        ("neoforge", ModLoader::NeoForge),
        ("forge", ModLoader::Forge),
        ("quilt-loader", ModLoader::Quilt),
        ("fabric-loader", ModLoader::Fabric),
    ] {
        if let Some(version) = deps.get(key) {
            return (loader, Some(version.clone()));
        }
    }
    (ModLoader::Vanilla, None)
}

fn read_index(zip_path: &Path) -> Result<PackIndex, String> {
    let file = std::fs::File::open(zip_path).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file)
        .map_err(|_| "This file is not a valid .mrpack archive.".to_string())?;
    let mut entry = archive.by_name(INDEX_FILE).map_err(|_| {
        "modrinth.index.json is missing; this is not a Modrinth modpack.".to_string()
    })?;
    let mut text = String::new();
    std::io::Read::read_to_string(&mut entry, &mut text).map_err(|e| e.to_string())?;
    let index: PackIndex =
        serde_json::from_str(&text).map_err(|e| format!("Invalid modpack index: {e}"))?;
    if index.game != "minecraft" {
        return Err("This modpack is not for Minecraft.".to_string());
    }
    Ok(index)
}

async fn run_install(
    app: AppHandle,
    pool: SqlitePool,
    profiles_dir: PathBuf,
    caches_dir: PathBuf,
    instance: Instance,
    source: PackSource,
) -> Result<(), String> {
    let instance_id = instance.id.to_string();
    let repo = InstanceRepository::new(pool);
    let profile_dir = profiles_dir.join(&instance.folder_name);
    let temp_dir = caches_dir.join("tmp").join(Uuid::new_v4().to_string());
    tokio::fs::create_dir_all(&temp_dir)
        .await
        .map_err(|e| e.to_string())?;
    let client = util::http_client()?;

    let result: Result<(), String> = async {
        let zip_path = match &source {
            PackSource::Remote(url) => {
                emit_progress(&app, &instance_id, "downloading_archive", "Downloading modpack");
                let path = temp_dir.join("pack.mrpack");
                util::download_verified(&client, url, &path, &ExpectedHash::NONE).await?;
                path
            }
            PackSource::Local(path) => path.clone(),
        };

        let index = read_index(&zip_path)?;
        let (loader, loader_version) = loader_from_dependencies(&index.dependencies);
        let mc_version = index
            .dependencies
            .get("minecraft")
            .cloned()
            .ok_or_else(|| "The modpack does not declare a Minecraft version.".to_string())?;

        let update = UpdateInstanceRequest {
            minecraft_version: Some(mc_version.clone()),
            mod_loader: Some(loader),
            loader_version: Some(loader_version.unwrap_or_default()),
            ..Default::default()
        };
        if let Ok(Some(updated)) = repo.update(instance.id, update).await {
            let _ = app.emit_to(
                "main",
                "modpack://instance-updated",
                serde_json::json!({ "instance_id": instance_id, "minecraft_version": updated.minecraft_version }),
            );
        }

        emit_progress(&app, &instance_id, "extracting", "Unpacking modpack");
        let extract_dir = temp_dir.join("extract");
        let zip_for_blocking = zip_path.clone();
        let extract_for_blocking = extract_dir.clone();
        tokio::task::spawn_blocking(move || util::extract_zip_safe(&zip_for_blocking, &extract_for_blocking, MAX_PACK_BYTES))
            .await
            .map_err(|e| e.to_string())??;

        let meta_dir = profile_dir.join(META_DIR);
        tokio::fs::create_dir_all(&meta_dir).await.map_err(|e| e.to_string())?;
        let serialized = serde_json::to_string_pretty(&index).map_err(|e| e.to_string())?;
        tokio::fs::write(meta_dir.join("modpack-index.json"), serialized).await.map_err(|e| e.to_string())?;

        let files: Vec<&PackFile> = index
            .files
            .iter()
            .filter(|f| f.env.as_ref().and_then(|e| e.get("client")).map(|c| c != "unsupported").unwrap_or(true))
            .collect();

        let hashes: Vec<String> = files.iter().filter_map(|f| f.hashes.get("sha1").cloned()).collect();
        emit_progress(&app, &instance_id, "resolving", "Looking up mod details");
        let modrinth = modrinth_client::ModrinthClient::new().map_err(|e| e.to_string())?;
        let versions: HashMap<String, Version> = modrinth.get_versions_from_hashes(&hashes).await.unwrap_or_default();
        let project_ids: Vec<String> = versions.values().map(|v| v.project_id.clone()).collect::<HashSet<_>>().into_iter().collect();
        let mut projects: HashMap<String, Project> = HashMap::new();
        for chunk in project_ids.chunks(100) {
            if let Ok(list) = modrinth.get_projects_by_ids(chunk).await {
                for p in list {
                    projects.insert(p.id.clone(), p);
                }
            }
        }

        let total = files.len().max(1);
        let mut skipped = Vec::new();
        for (index_pos, file) in files.iter().enumerate() {
            let dest = match util::safe_relative_join(&profile_dir, &file.path) {
                Some(d) => d,
                None => {
                    skipped.push(format!("{} (unsafe path)", file.path));
                    continue;
                }
            };
            let url = match file.downloads.iter().find(|u| util::is_modrinth_pack_host(u)) {
                Some(u) => u.clone(),
                None => {
                    skipped.push(format!("{} (untrusted download host)", file.path));
                    continue;
                }
            };
            let sha1 = file.hashes.get("sha1").map(String::as_str);
            let sha512 = file.hashes.get("sha512").map(String::as_str);
            if sha1.is_none() && sha512.is_none() {
                skipped.push(format!("{} (no checksum)", file.path));
                continue;
            }
            emit_progress(
                &app,
                &instance_id,
                "downloading_mods",
                &format!("Downloading files ({}/{})", index_pos + 1, total),
            );
            util::download_verified(&client, &url, &dest, &ExpectedHash { sha1, sha512 }).await?;

            let normalized = file.path.replace('\\', "/");
            let (folder, name) = match normalized.rsplit_once('/') {
                Some((f, n)) => (f.to_string(), n.to_string()),
                None => continue,
            };
            let project_type = match folder.as_str() {
                "mods" => "mod",
                "resourcepacks" => "resourcepack",
                "shaderpacks" => "shader",
                _ => continue,
            };
            if !is_safe_file_name(&name) || content_folder(project_type).is_none() {
                continue;
            }
            let version = sha1.and_then(|h| versions.get(h));
            let project = version.and_then(|v| projects.get(&v.project_id));
            let record = NewInstanceMod {
                instance_id: instance_id.clone(),
                project_id: version.map(|v| v.project_id.clone()).unwrap_or_else(|| format!("local:{name}")),
                project_type: project_type.to_string(),
                title: project
                    .map(|p| p.title.clone())
                    .unwrap_or_else(|| name.trim_end_matches(".jar").trim_end_matches(".zip").to_string()),
                icon_url: project.and_then(|p| p.icon_url.clone()),
                version_id: version.map(|v| v.id.clone()),
                version_name: version.map(|v| v.version_number.clone()),
                file_url: Some(url),
                file_name: Some(name),
                sha1: sha1.map(String::from),
                enabled: true,
            };
            let _ = repo.add_mod(&record).await;
        }

        emit_progress(&app, &instance_id, "copying_overrides", "Applying configuration");
        for overrides in ["overrides", "client-overrides"] {
            let dir = extract_dir.join(overrides);
            if dir.exists() {
                let dst = profile_dir.clone();
                tokio::task::spawn_blocking(move || util::copy_dir_recursive(&dir, &dst, &[]))
                    .await
                    .map_err(|e| e.to_string())??;
            }
        }

        if !skipped.is_empty() {
            tracing::warn!("modpack {} skipped {} file(s): {}", instance.name, skipped.len(), skipped.join(", "));
        }
        Ok(())
    }
    .await;

    let _ = tokio::fs::remove_dir_all(&temp_dir).await;
    result?;

    let _ = app.emit_to(
        "main",
        "modpack://complete",
        serde_json::json!({ "instance_id": instance_id, "name": instance.name }),
    );
    Ok(())
}

fn spawn_install(app: AppHandle, state: &AppState, instance: Instance, source: PackSource) {
    let pool = state.pool.clone();
    let profiles_dir = state.profiles_dir.clone();
    let caches_dir = state.caches_dir.clone();
    tauri::async_runtime::spawn(async move {
        let instance_id = instance.id;
        let folder = profiles_dir.join(&instance.folder_name);
        if let Err(err) = run_install(
            app.clone(),
            pool.clone(),
            profiles_dir,
            caches_dir,
            instance,
            source,
        )
        .await
        {
            tracing::error!("modpack install failed: {err}");
            let _ = app.emit_to(
                "main",
                "modpack://error",
                serde_json::json!({ "instance_id": instance_id.to_string(), "message": err }),
            );
            let _ = InstanceRepository::new(pool).delete(instance_id).await;
            let _ = std::fs::remove_dir_all(folder);
        }
    });
}

async fn create_placeholder(
    state: &AppState,
    name: String,
    description: String,
    mc: String,
    loader: ModLoader,
) -> Result<Instance, String> {
    let instance = state
        .instances
        .create(CreateInstanceRequest {
            name,
            description,
            minecraft_version: mc,
            mod_loader: loader,
            loader_version: None,
        })
        .await
        .map_err(|e| e.to_string())?;
    let dir = state.profiles_dir.join(&instance.folder_name);
    std::fs::create_dir_all(dir.join("mods")).map_err(|e| e.to_string())?;
    Ok(instance)
}

#[tauri::command]
pub async fn create_instance_from_modpack(
    app: AppHandle,
    state: State<'_, AppState>,
    project_id: String,
    version_id: Option<String>,
    name: String,
) -> Result<Instance, String> {
    let name = name.trim().to_string();
    if name.is_empty() {
        return Err("Instance name cannot be empty.".to_string());
    }
    let project = state
        .modrinth
        .get_project(&project_id)
        .await
        .map_err(|e| format!("failed to fetch project: {e}"))?;
    if project.project_type != ProjectType::Modpack {
        return Err("Selected project is not a modpack".to_string());
    }
    let versions = state
        .modrinth
        .list_versions(&project_id)
        .await
        .map_err(|e| format!("failed to fetch modpack versions: {e}"))?;
    let selected = match version_id.as_deref().filter(|v| !v.is_empty()) {
        Some(vid) => versions
            .iter()
            .find(|v| v.id == vid)
            .ok_or_else(|| "Selected modpack version not found".to_string())?,
        None => versions
            .iter()
            .find(|v| v.version_type == VersionType::Release)
            .or_else(|| versions.first())
            .ok_or_else(|| "No modpack version available".to_string())?,
    };
    let file = selected
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| selected.files.first())
        .ok_or_else(|| "No modpack file available".to_string())?;
    let url = file.url.clone();

    let mc = selected
        .game_versions
        .first()
        .cloned()
        .unwrap_or_else(|| "1.20.1".to_string());
    let loader = selected
        .loaders
        .iter()
        .map(|l| ModLoader::parse(l))
        .find(|l| *l != ModLoader::Vanilla)
        .unwrap_or(ModLoader::Vanilla);
    let mut instance =
        create_placeholder(&state, name, project.description.clone(), mc, loader).await?;
    if let Some(icon) = project.icon_url.clone() {
        let update = UpdateInstanceRequest {
            icon_url: Some(icon),
            ..Default::default()
        };
        if let Ok(Some(updated)) = state.instances.update(instance.id, update).await {
            instance = updated;
        }
    }
    spawn_install(app, &state, instance.clone(), PackSource::Remote(url));
    Ok(instance)
}

#[tauri::command]
pub async fn import_modpack_file(
    app: AppHandle,
    state: State<'_, AppState>,
    path: String,
    name: Option<String>,
) -> Result<Instance, String> {
    let source = PathBuf::from(&path);
    let meta = tokio::fs::metadata(&source)
        .await
        .map_err(|_| "The selected file does not exist.".to_string())?;
    if !meta.is_file() || meta.len() > MAX_PACK_BYTES {
        return Err("The selected file is not a usable modpack.".to_string());
    }
    let index = {
        let source = source.clone();
        tokio::task::spawn_blocking(move || read_index(&source))
            .await
            .map_err(|e| e.to_string())??
    };
    let (loader, _) = loader_from_dependencies(&index.dependencies);
    let mc = index
        .dependencies
        .get("minecraft")
        .cloned()
        .unwrap_or_default();
    let display_name = name
        .map(|n| n.trim().to_string())
        .filter(|n| !n.is_empty())
        .or_else(|| Some(index.name.trim().to_string()).filter(|n| !n.is_empty()))
        .unwrap_or_else(|| "Imported modpack".to_string());
    let instance = create_placeholder(
        &state,
        display_name,
        index.summary.clone().unwrap_or_default(),
        mc,
        loader,
    )
    .await?;
    spawn_install(app, &state, instance.clone(), PackSource::Local(source));
    Ok(instance)
}

#[tauri::command]
pub async fn export_instance_modpack(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<String, String> {
    let instance = load_instance(&state, &instance_id).await?;
    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    let entries = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;

    let mut files: Vec<PackFile> = Vec::new();
    let mut local_overrides: Vec<(String, PathBuf)> = Vec::new();
    for entry in entries.iter().filter(|e| e.enabled) {
        let folder = match content_folder(&entry.project_type) {
            Some(f) => f,
            None => continue,
        };
        let file_name = match entry.file_name.as_deref().filter(|n| is_safe_file_name(n)) {
            Some(n) => n,
            None => continue,
        };
        let path = profile_dir.join(folder).join(file_name);
        if !path.exists() {
            continue;
        }
        let rel = format!("{folder}/{file_name}");
        match entry
            .file_url
            .as_deref()
            .filter(|u| util::is_modrinth_pack_host(u))
        {
            Some(url) => {
                let (sha1, sha512, size) = util::hashes_of_file(&path).await?;
                let mut hashes = HashMap::new();
                hashes.insert("sha1".to_string(), sha1);
                hashes.insert("sha512".to_string(), sha512);
                files.push(PackFile {
                    path: rel,
                    hashes,
                    env: None,
                    downloads: vec![url.to_string()],
                    file_size: size,
                });
            }
            None => local_overrides.push((rel, path)),
        }
    }

    let mut dependencies = HashMap::new();
    dependencies.insert("minecraft".to_string(), instance.minecraft_version.clone());
    if let Some(version) = instance.loader_version.clone() {
        let key = match instance.mod_loader {
            ModLoader::Fabric | ModLoader::Modrinth => Some("fabric-loader"),
            ModLoader::Quilt => Some("quilt-loader"),
            ModLoader::Forge => Some("forge"),
            ModLoader::NeoForge => Some("neoforge"),
            ModLoader::Vanilla => None,
        };
        if let Some(key) = key {
            dependencies.insert(key.to_string(), version);
        }
    }

    let index = PackIndex {
        format_version: 1,
        game: "minecraft".to_string(),
        version_id: "1.0.0".to_string(),
        name: instance.name.clone(),
        summary: Some(instance.description.clone()).filter(|d| !d.is_empty()),
        files,
        dependencies,
    };

    let downloads = app
        .path()
        .download_dir()
        .map_err(|e| format!("Could not find the Downloads folder: {e}"))?;
    tokio::fs::create_dir_all(&downloads)
        .await
        .map_err(|e| e.to_string())?;
    let target = util::unique_path(
        &downloads,
        &format!("{}.mrpack", util::sanitize_file_name(&instance.name)),
    );
    let target_for_blocking = target.clone();
    let index_json = serde_json::to_vec_pretty(&index).map_err(|e| e.to_string())?;

    tokio::task::spawn_blocking(move || -> Result<(), String> {
        let file = std::fs::File::create(&target_for_blocking).map_err(|e| e.to_string())?;
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);
        zip.start_file(INDEX_FILE, options)
            .map_err(|e| e.to_string())?;
        zip.write_all(&index_json).map_err(|e| e.to_string())?;
        for (rel, path) in local_overrides {
            zip.start_file(format!("overrides/{rel}"), options)
                .map_err(|e| e.to_string())?;
            let mut source = std::fs::File::open(&path).map_err(|e| e.to_string())?;
            std::io::copy(&mut source, &mut zip).map_err(|e| e.to_string())?;
        }
        for extra in ["config", "options.txt", "servers.dat"] {
            let path = profile_dir.join(extra);
            if path.is_file() {
                zip.start_file(format!("overrides/{extra}"), options)
                    .map_err(|e| e.to_string())?;
                let mut source = std::fs::File::open(&path).map_err(|e| e.to_string())?;
                std::io::copy(&mut source, &mut zip).map_err(|e| e.to_string())?;
            } else if path.is_dir() {
                add_dir_to_zip(&mut zip, &path, &format!("overrides/{extra}"), options)?;
            }
        }
        zip.finish().map_err(|e| e.to_string())?;
        Ok(())
    })
    .await
    .map_err(|e| e.to_string())??;

    Ok(target.to_string_lossy().to_string())
}

fn add_dir_to_zip(
    zip: &mut zip::ZipWriter<std::fs::File>,
    dir: &Path,
    prefix: &str,
    options: zip::write::SimpleFileOptions,
) -> Result<(), String> {
    for entry in std::fs::read_dir(dir).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let file_type = entry.file_type().map_err(|e| e.to_string())?;
        if file_type.is_symlink() {
            continue;
        }
        let name = format!("{}/{}", prefix, entry.file_name().to_string_lossy());
        if file_type.is_dir() {
            add_dir_to_zip(zip, &entry.path(), &name, options)?;
        } else {
            zip.start_file(name, options).map_err(|e| e.to_string())?;
            let mut source = std::fs::File::open(entry.path()).map_err(|e| e.to_string())?;
            std::io::copy(&mut source, zip).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}
