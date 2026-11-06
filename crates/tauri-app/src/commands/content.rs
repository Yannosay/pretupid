use std::collections::{HashMap, HashSet, VecDeque};
use std::path::{Path, PathBuf};

use modrinth_client::models::{DependencyType, Project, ProjectType, Version, VersionType};
use pretupid_core::instance::{
    content_folder, is_safe_file_name, Instance, InstanceMod, ModLoader, NewInstanceMod,
};
use serde::Serialize;
use tauri::State;
use uuid::Uuid;

use super::util::{self, ExpectedHash};
use crate::state::AppState;

const DISABLED_SUFFIX: &str = ".disabled";
const MAX_DEPENDENCIES: usize = 48;
const MAX_IMPORT_BYTES: u64 = 1024 * 1024 * 1024;

#[derive(Debug, Serialize)]
pub struct InstallResult {
    pub installed: Vec<InstanceMod>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct ModUpdate {
    pub mod_id: String,
    pub title: String,
    pub icon_url: Option<String>,
    pub current_version: Option<String>,
    pub new_version_id: String,
    pub new_version_name: String,
}

pub async fn load_instance(state: &AppState, instance_id: &str) -> Result<Instance, String> {
    let uuid = Uuid::parse_str(instance_id).map_err(|_| "Invalid instance id".to_string())?;
    state
        .instances
        .get_by_id(uuid)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Instance not found".to_string())
}

pub fn project_type_name(kind: ProjectType) -> &'static str {
    match kind {
        ProjectType::Mod => "mod",
        ProjectType::Modpack => "modpack",
        ProjectType::ResourcePack => "resourcepack",
        ProjectType::Shader => "shader",
        ProjectType::Unknown => "unknown",
    }
}

pub fn content_dir(
    state: &AppState,
    instance: &Instance,
    project_type: &str,
) -> Result<PathBuf, String> {
    let folder = content_folder(project_type)
        .ok_or_else(|| format!("'{}' content cannot be added to an instance", project_type))?;
    Ok(state.profiles_dir.join(&instance.folder_name).join(folder))
}

pub fn content_file_path(
    state: &AppState,
    instance: &Instance,
    entry: &InstanceMod,
) -> Option<PathBuf> {
    let file_name = entry
        .file_name
        .as_deref()
        .filter(|n| is_safe_file_name(n))?;
    let dir = content_dir(state, instance, &entry.project_type).ok()?;
    let name = if entry.enabled {
        file_name.to_string()
    } else {
        format!("{file_name}{DISABLED_SUFFIX}")
    };
    Some(dir.join(name))
}

fn pretty_title(file_name: &str) -> String {
    let stem = file_name
        .trim_end_matches(DISABLED_SUFFIX)
        .trim_end_matches(".jar")
        .trim_end_matches(".zip");
    stem.replace(['_', '+'], " ").trim().to_string()
}

fn pick_primary_file(version: &Version) -> Option<&modrinth_client::models::VersionFile> {
    version
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| version.files.first())
}

fn pick_best_version(mut versions: Vec<Version>) -> Option<Version> {
    if let Some(index) = versions
        .iter()
        .position(|v| v.version_type == VersionType::Release)
    {
        return Some(versions.swap_remove(index));
    }
    if versions.is_empty() {
        None
    } else {
        Some(versions.swap_remove(0))
    }
}

fn instance_loaders(instance: &Instance) -> Vec<String> {
    instance
        .mod_loader
        .compatible_mod_loaders(&instance.minecraft_version)
        .into_iter()
        .map(String::from)
        .collect()
}

fn check_version_compat(
    instance: &Instance,
    project_type: &str,
    version: &Version,
) -> Result<(), String> {
    if project_type != "mod" {
        return Ok(());
    }
    if !version
        .game_versions
        .iter()
        .any(|gv| gv == &instance.minecraft_version)
    {
        return Err(format!(
            "{} does not support Minecraft {}",
            version.name, instance.minecraft_version
        ));
    }
    let loaders = instance_loaders(instance);
    if !version.loaders.iter().any(|l| loaders.contains(l)) {
        return Err(format!(
            "{} is made for {}, but this instance uses {}",
            version.name,
            version.loaders.join("/"),
            instance.mod_loader.as_str()
        ));
    }
    Ok(())
}

async fn resolve_version_for(
    state: &AppState,
    instance: &Instance,
    project_id: &str,
    project_type: &str,
) -> Result<Option<Version>, String> {
    let loaders = if project_type == "mod" {
        instance_loaders(instance)
    } else {
        Vec::new()
    };
    let versions = state
        .modrinth
        .list_versions_filtered(
            project_id,
            &loaders,
            std::slice::from_ref(&instance.minecraft_version),
        )
        .await
        .map_err(|e| e.to_string())?;
    if !versions.is_empty() || project_type == "mod" {
        return Ok(pick_best_version(versions));
    }
    let any = state
        .modrinth
        .list_versions(project_id)
        .await
        .map_err(|e| e.to_string())?;
    Ok(pick_best_version(any))
}

async fn install_version(
    state: &AppState,
    instance: &Instance,
    project: &Project,
    version: &Version,
    project_type: &str,
) -> Result<InstanceMod, String> {
    let file = pick_primary_file(version)
        .ok_or_else(|| format!("{} has no downloadable file", version.name))?;
    let file_name = util::sanitize_file_name(&file.filename);
    if !is_safe_file_name(&file_name) {
        return Err(format!("{} has an unsafe file name", project.title));
    }
    let dir = content_dir(state, instance, project_type)?;
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|e| e.to_string())?;
    let dest = dir.join(&file_name);

    let client = util::http_client()?;
    let expected = ExpectedHash {
        sha1: Some(file.hashes.sha1.as_str()).filter(|s| !s.is_empty()),
        sha512: Some(file.hashes.sha512.as_str()).filter(|s| !s.is_empty()),
    };
    let sha1 = util::download_verified(&client, &file.url, &dest, &expected).await?;

    let record = NewInstanceMod {
        instance_id: instance.id.to_string(),
        project_id: project.id.clone(),
        project_type: project_type.to_string(),
        title: project.title.clone(),
        icon_url: project.icon_url.clone(),
        version_id: Some(version.id.clone()),
        version_name: Some(version.version_number.clone()),
        file_url: Some(file.url.clone()),
        file_name: Some(file_name),
        sha1: Some(sha1),
        enabled: true,
    };
    state
        .instances
        .add_mod(&record)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn list_instance_mods(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<InstanceMod>, String> {
    let instance = load_instance(&state, &instance_id).await?;
    reconcile_disk(&state, &instance).await?;
    state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())
}

async fn reconcile_disk(state: &AppState, instance: &Instance) -> Result<(), String> {
    let instance_id = instance.id.to_string();
    let existing = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;

    let mut known: HashMap<(String, String), &InstanceMod> = HashMap::new();
    for entry in &existing {
        if let Some(name) = &entry.file_name {
            let folder = content_folder(&entry.project_type)
                .unwrap_or("mods")
                .to_string();
            known.insert((folder, name.to_lowercase()), entry);
        }
    }

    let mut seen: HashSet<String> = HashSet::new();
    for (project_type, extension) in [
        ("mod", ".jar"),
        ("resourcepack", ".zip"),
        ("shader", ".zip"),
    ] {
        let dir = match content_dir(state, instance, project_type) {
            Ok(d) => d,
            Err(_) => continue,
        };
        let folder = content_folder(project_type).unwrap_or("mods").to_string();
        let mut entries = match tokio::fs::read_dir(&dir).await {
            Ok(e) => e,
            Err(_) => continue,
        };
        while let Ok(Some(entry)) = entries.next_entry().await {
            let is_file = entry
                .file_type()
                .await
                .map(|t| t.is_file())
                .unwrap_or(false);
            if !is_file {
                continue;
            }
            let raw = entry.file_name().to_string_lossy().to_string();
            let enabled = !raw.ends_with(DISABLED_SUFFIX);
            let base = raw.trim_end_matches(DISABLED_SUFFIX).to_string();
            if !base.to_lowercase().ends_with(extension) || !is_safe_file_name(&base) {
                continue;
            }
            let key = (folder.clone(), base.to_lowercase());
            match known.get(&key) {
                Some(existing) => {
                    seen.insert(existing.id.clone());
                    if existing.enabled != enabled {
                        let _ = state.instances.set_mod_enabled(&existing.id, enabled).await;
                    }
                }
                None => {
                    let record = NewInstanceMod {
                        instance_id: instance_id.clone(),
                        project_id: format!("local:{}", base),
                        project_type: project_type.to_string(),
                        title: pretty_title(&base),
                        file_name: Some(base.clone()),
                        enabled,
                        ..Default::default()
                    };
                    if let Ok(created) = state.instances.add_mod(&record).await {
                        seen.insert(created.id);
                    }
                }
            }
        }
    }

    for entry in &existing {
        let has_source = entry
            .file_url
            .as_deref()
            .map(|u| !u.is_empty())
            .unwrap_or(false);
        if !seen.contains(&entry.id) && !has_source {
            let _ = state.instances.remove_mod(&entry.id).await;
        }
    }
    Ok(())
}

#[tauri::command]
pub async fn add_instance_mod(
    state: State<'_, AppState>,
    instance_id: String,
    project_id: String,
    version_id: Option<String>,
    install_dependencies: Option<bool>,
) -> Result<InstallResult, String> {
    let instance = load_instance(&state, &instance_id).await?;
    let project = state
        .modrinth
        .get_project(&project_id)
        .await
        .map_err(|e| format!("Could not load the project from Modrinth: {e}"))?;
    let project_type = project_type_name(project.project_type);

    match project_type {
        "modpack" => return Err("Modpacks create their own instance. Use \"Install modpack\" instead.".to_string()),
        "mod" if instance.mod_loader == ModLoader::Vanilla => {
            return Err("Vanilla instances cannot load mods. Create a Fabric, Quilt, Forge or NeoForge instance first.".to_string())
        }
        "unknown" => return Err("This kind of project cannot be installed into an instance.".to_string()),
        _ => {}
    }

    let existing = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;
    if existing.iter().any(|m| m.project_id == project.id) {
        return Err(format!(
            "{} is already installed in this instance.",
            project.title
        ));
    }

    let version = match version_id.as_deref().filter(|v| !v.is_empty()) {
        Some(vid) => {
            let version = state
                .modrinth
                .get_version(vid)
                .await
                .map_err(|e| e.to_string())?;
            if version.project_id != project.id {
                return Err("The selected version belongs to a different project.".to_string());
            }
            version
        }
        None => resolve_version_for(&state, &instance, &project.id, project_type)
            .await?
            .ok_or_else(|| {
                format!(
                    "{} has no version for Minecraft {} with {}.",
                    project.title,
                    instance.minecraft_version,
                    instance.mod_loader.as_str()
                )
            })?,
    };
    check_version_compat(&instance, project_type, &version)?;

    let mut warnings = Vec::new();
    let installed_ids: HashSet<String> = existing.iter().map(|m| m.project_id.clone()).collect();
    for dep in &version.dependencies {
        if dep.dependency_type == DependencyType::Incompatible {
            if let Some(pid) = &dep.project_id {
                if installed_ids.contains(pid) {
                    let title = existing
                        .iter()
                        .find(|m| &m.project_id == pid)
                        .map(|m| m.title.clone())
                        .unwrap_or_default();
                    warnings.push(format!(
                        "{} is marked as incompatible with {}.",
                        project.title, title
                    ));
                }
            }
        }
    }

    let primary = install_version(&state, &instance, &project, &version, project_type).await?;
    let mut installed = vec![primary];

    if project_type == "mod" && install_dependencies.unwrap_or(true) {
        let mut done: HashSet<String> = installed_ids;
        done.insert(project.id.clone());
        let mut queue: VecDeque<Version> = VecDeque::from([version]);
        while let Some(current) = queue.pop_front() {
            for dep in &current.dependencies {
                if dep.dependency_type != DependencyType::Required
                    || installed.len() > MAX_DEPENDENCIES
                {
                    continue;
                }
                let dep_version = match (&dep.version_id, &dep.project_id) {
                    (Some(vid), _) => state.modrinth.get_version(vid).await.ok(),
                    (None, Some(pid)) => resolve_version_for(&state, &instance, pid, "mod")
                        .await
                        .ok()
                        .flatten(),
                    _ => None,
                };
                let dep_version = match dep_version {
                    Some(v) => v,
                    None => {
                        if let Some(pid) = &dep.project_id {
                            if !done.contains(pid) {
                                warnings.push(format!(
                                    "A required dependency ({pid}) has no compatible version."
                                ));
                            }
                        }
                        continue;
                    }
                };
                if !done.insert(dep_version.project_id.clone()) {
                    continue;
                }
                let dep_project = match state.modrinth.get_project(&dep_version.project_id).await {
                    Ok(p) => p,
                    Err(e) => {
                        warnings.push(format!(
                            "Could not load dependency {}: {e}",
                            dep_version.project_id
                        ));
                        continue;
                    }
                };
                if let Err(e) = check_version_compat(&instance, "mod", &dep_version) {
                    warnings.push(format!("Skipped dependency {}: {e}", dep_project.title));
                    continue;
                }
                match install_version(&state, &instance, &dep_project, &dep_version, "mod").await {
                    Ok(record) => {
                        installed.push(record);
                        queue.push_back(dep_version);
                    }
                    Err(e) => warnings.push(format!(
                        "Failed to install dependency {}: {e}",
                        dep_project.title
                    )),
                }
            }
        }
    }

    Ok(InstallResult {
        installed,
        warnings,
    })
}

#[tauri::command]
pub async fn remove_instance_mod(
    state: State<'_, AppState>,
    mod_id: String,
) -> Result<bool, String> {
    let entry = match state
        .instances
        .get_mod_by_id(&mod_id)
        .await
        .map_err(|e| e.to_string())?
    {
        Some(e) => e,
        None => return Ok(false),
    };
    let instance = load_instance(&state, &entry.instance_id).await?;
    if let Some(file_name) = entry.file_name.as_deref().filter(|n| is_safe_file_name(n)) {
        if let Ok(dir) = content_dir(&state, &instance, &entry.project_type) {
            let _ = tokio::fs::remove_file(dir.join(file_name)).await;
            let _ = tokio::fs::remove_file(dir.join(format!("{file_name}{DISABLED_SUFFIX}"))).await;
        }
    }
    state
        .instances
        .remove_mod(&mod_id)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn set_instance_mod_enabled(
    state: State<'_, AppState>,
    mod_id: String,
    enabled: bool,
) -> Result<InstanceMod, String> {
    let entry = state
        .instances
        .get_mod_by_id(&mod_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Content entry not found".to_string())?;
    let instance = load_instance(&state, &entry.instance_id).await?;
    if entry.enabled != enabled {
        if let Some(file_name) = entry.file_name.as_deref().filter(|n| is_safe_file_name(n)) {
            let dir = content_dir(&state, &instance, &entry.project_type)?;
            let active = dir.join(file_name);
            let disabled = dir.join(format!("{file_name}{DISABLED_SUFFIX}"));
            let (from, to) = if enabled {
                (disabled, active)
            } else {
                (active, disabled)
            };
            if from.exists() {
                tokio::fs::rename(&from, &to)
                    .await
                    .map_err(|e| format!("Could not rename the file: {e}"))?;
            }
        }
        state
            .instances
            .set_mod_enabled(&mod_id, enabled)
            .await
            .map_err(|e| e.to_string())?;
    }
    state
        .instances
        .get_mod_by_id(&mod_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Content entry not found".to_string())
}

async fn hashed_entries(
    state: &AppState,
    instance: &Instance,
) -> Result<Vec<(InstanceMod, String)>, String> {
    let entries = state
        .instances
        .list_mods(&instance.id.to_string())
        .await
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for entry in entries {
        let path = match content_file_path(state, instance, &entry) {
            Some(p) if p.exists() => p,
            _ => continue,
        };
        let hash = match util::sha1_file(&path).await {
            Ok(h) => h,
            Err(_) => continue,
        };
        if entry.sha1.as_deref() != Some(hash.as_str()) {
            let _ = state.instances.set_mod_sha1(&entry.id, &hash).await;
        }
        out.push((entry, hash));
    }
    Ok(out)
}

async fn identify_local_entries(state: &AppState, entries: &[(InstanceMod, String)]) {
    let local: Vec<&(InstanceMod, String)> = entries
        .iter()
        .filter(|(m, _)| m.project_id.starts_with("local:"))
        .collect();
    if local.is_empty() {
        return;
    }
    let hashes: Vec<String> = local.iter().map(|(_, h)| h.clone()).collect();
    let versions = match state.modrinth.get_versions_from_hashes(&hashes).await {
        Ok(v) => v,
        Err(_) => return,
    };
    let project_ids: Vec<String> = versions
        .values()
        .map(|v| v.project_id.clone())
        .collect::<HashSet<_>>()
        .into_iter()
        .collect();
    let projects: HashMap<String, Project> = state
        .modrinth
        .get_projects_by_ids(&project_ids)
        .await
        .map(|list| list.into_iter().map(|p| (p.id.clone(), p)).collect())
        .unwrap_or_default();
    for (entry, hash) in local {
        if let Some(version) = versions.get(hash) {
            let title = projects
                .get(&version.project_id)
                .map(|p| p.title.clone())
                .unwrap_or_else(|| entry.title.clone());
            let icon = projects
                .get(&version.project_id)
                .and_then(|p| p.icon_url.clone());
            let _ = state
                .instances
                .update_mod_identity(
                    &entry.id,
                    &version.project_id,
                    &title,
                    icon.as_deref(),
                    Some(&version.id),
                    Some(&version.version_number),
                )
                .await;
        }
    }
}

async fn find_updates(
    state: &AppState,
    instance: &Instance,
) -> Result<Vec<(InstanceMod, Version)>, String> {
    let entries = hashed_entries(state, instance).await?;
    identify_local_entries(state, &entries).await;

    let game_versions = vec![instance.minecraft_version.clone()];
    let mut updates = Vec::new();
    for group in ["mod", "other"] {
        let selected: Vec<&(InstanceMod, String)> = entries
            .iter()
            .filter(|(m, _)| (m.project_type == "mod") == (group == "mod"))
            .collect();
        if selected.is_empty() {
            continue;
        }
        let hashes: Vec<String> = selected.iter().map(|(_, h)| h.clone()).collect();
        let loaders = if group == "mod" {
            instance_loaders(instance)
        } else {
            Vec::new()
        };
        let latest = state
            .modrinth
            .get_latest_versions_from_hashes(&hashes, &loaders, &game_versions)
            .await
            .map_err(|e| format!("Update check failed: {e}"))?;
        for (entry, hash) in selected {
            if let Some(version) = latest.get(hash) {
                let same_file = version
                    .files
                    .iter()
                    .any(|f| f.hashes.sha1.eq_ignore_ascii_case(hash));
                if !same_file && entry.version_id.as_deref() != Some(version.id.as_str()) {
                    updates.push((entry.clone(), version.clone()));
                }
            }
        }
    }
    Ok(updates)
}

#[tauri::command]
pub async fn check_instance_updates(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<ModUpdate>, String> {
    let instance = load_instance(&state, &instance_id).await?;
    let updates = find_updates(&state, &instance).await?;
    let refreshed: HashMap<String, InstanceMod> = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?
        .into_iter()
        .map(|m| (m.id.clone(), m))
        .collect();
    Ok(updates
        .into_iter()
        .map(|(entry, version)| {
            let current = refreshed.get(&entry.id).cloned().unwrap_or(entry);
            ModUpdate {
                mod_id: current.id,
                title: current.title,
                icon_url: current.icon_url,
                current_version: current.version_name,
                new_version_id: version.id,
                new_version_name: version.version_number,
            }
        })
        .collect())
}

#[tauri::command]
pub async fn update_instance_mods(
    state: State<'_, AppState>,
    instance_id: String,
    mod_ids: Option<Vec<String>>,
) -> Result<InstallResult, String> {
    let instance = load_instance(&state, &instance_id).await?;
    let updates = find_updates(&state, &instance).await?;
    let wanted: Option<HashSet<String>> = mod_ids.map(|ids| ids.into_iter().collect());
    let client = util::http_client()?;

    let mut installed = Vec::new();
    let mut warnings = Vec::new();
    for (entry, version) in updates {
        if let Some(w) = &wanted {
            if !w.contains(&entry.id) {
                continue;
            }
        }
        let file = match pick_primary_file(&version) {
            Some(f) => f,
            None => continue,
        };
        let new_name = util::sanitize_file_name(&file.filename);
        if !is_safe_file_name(&new_name) {
            warnings.push(format!("{}: unsafe file name", entry.title));
            continue;
        }
        let dir = match content_dir(&state, &instance, &entry.project_type) {
            Ok(d) => d,
            Err(e) => {
                warnings.push(e);
                continue;
            }
        };
        let target_name = if entry.enabled {
            new_name.clone()
        } else {
            format!("{new_name}{DISABLED_SUFFIX}")
        };
        let staging = dir.join(format!(".pretupid-update-{}", Uuid::new_v4()));
        let expected = ExpectedHash {
            sha1: Some(file.hashes.sha1.as_str()).filter(|s| !s.is_empty()),
            sha512: Some(file.hashes.sha512.as_str()).filter(|s| !s.is_empty()),
        };
        let sha1 = match util::download_verified(&client, &file.url, &staging, &expected).await {
            Ok(h) => h,
            Err(e) => {
                warnings.push(format!("{}: {e}", entry.title));
                continue;
            }
        };
        if let Some(old) = content_file_path(&state, &instance, &entry) {
            let _ = tokio::fs::remove_file(old).await;
        }
        if let Err(e) = tokio::fs::rename(&staging, dir.join(&target_name)).await {
            let _ = tokio::fs::remove_file(&staging).await;
            warnings.push(format!("{}: {e}", entry.title));
            continue;
        }
        state
            .instances
            .replace_mod_file(
                &entry.id,
                Some(&version.id),
                Some(&version.version_number),
                Some(&file.url),
                &new_name,
                Some(&sha1),
            )
            .await
            .map_err(|e| e.to_string())?;
        if let Ok(Some(updated)) = state.instances.get_mod_by_id(&entry.id).await {
            installed.push(updated);
        }
    }
    Ok(InstallResult {
        installed,
        warnings,
    })
}

fn zip_contains_shaders(path: &Path) -> bool {
    let file = match std::fs::File::open(path) {
        Ok(f) => f,
        Err(_) => return false,
    };
    let mut archive = match zip::ZipArchive::new(file) {
        Ok(a) => a,
        Err(_) => return false,
    };
    (0..archive.len()).any(|i| {
        archive
            .by_index(i)
            .map(|e| e.name().to_lowercase().starts_with("shaders/"))
            .unwrap_or(false)
    })
}

#[tauri::command]
pub async fn import_instance_files(
    state: State<'_, AppState>,
    instance_id: String,
    paths: Vec<String>,
) -> Result<InstallResult, String> {
    let instance = load_instance(&state, &instance_id).await?;
    let mut installed = Vec::new();
    let mut warnings = Vec::new();

    for raw in paths.into_iter().take(200) {
        let source = PathBuf::from(&raw);
        let meta = match tokio::fs::metadata(&source).await {
            Ok(m) if m.is_file() => m,
            _ => {
                warnings.push(format!("{raw} is not a file"));
                continue;
            }
        };
        if meta.len() > MAX_IMPORT_BYTES {
            warnings.push(format!("{raw} is larger than 1 GB"));
            continue;
        }
        let file_name = util::sanitize_file_name(
            &source
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_default(),
        );
        let lower = file_name.to_lowercase();
        let project_type = if lower.ends_with(".jar") {
            if instance.mod_loader == ModLoader::Vanilla {
                warnings.push(format!("{file_name}: vanilla instances cannot load mods"));
                continue;
            }
            "mod"
        } else if lower.ends_with(".zip") {
            if zip_contains_shaders(&source) {
                "shader"
            } else {
                "resourcepack"
            }
        } else if lower.ends_with(".mrpack") {
            warnings.push(format!(
                "{file_name}: modpacks are imported from the Library page"
            ));
            continue;
        } else {
            warnings.push(format!(
                "{file_name}: only .jar and .zip files can be added"
            ));
            continue;
        };

        let dir = content_dir(&state, &instance, project_type)?;
        tokio::fs::create_dir_all(&dir)
            .await
            .map_err(|e| e.to_string())?;
        let dest = util::unique_path(&dir, &file_name);
        if let Err(e) = tokio::fs::copy(&source, &dest).await {
            warnings.push(format!("{file_name}: {e}"));
            continue;
        }
        let stored_name = dest
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or(file_name.clone());
        let sha1 = util::sha1_file(&dest).await.ok();
        let record = NewInstanceMod {
            instance_id: instance_id.clone(),
            project_id: format!("local:{stored_name}"),
            project_type: project_type.to_string(),
            title: pretty_title(&stored_name),
            file_name: Some(stored_name),
            sha1,
            enabled: true,
            ..Default::default()
        };
        match state.instances.add_mod(&record).await {
            Ok(r) => installed.push(r),
            Err(e) => warnings.push(e.to_string()),
        }
    }

    let hashed: Vec<(InstanceMod, String)> = installed
        .iter()
        .filter_map(|m| m.sha1.clone().map(|h| (m.clone(), h)))
        .collect();
    identify_local_entries(&state, &hashed).await;

    Ok(InstallResult {
        installed,
        warnings,
    })
}
