use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use base64::{engine::general_purpose::STANDARD, Engine as _};
use tauri::State;
use tokio::sync::Mutex;

use super::util::{self, ExpectedHash};
use crate::state::AppState;

static JAR_DOWNLOAD: Mutex<()> = Mutex::const_new(());

const MAX_TEXTURES: usize = 64;
const MAX_TEXTURE_BYTES: u64 = 256 * 1024;
const PNG_SIGNATURE: &[u8] = b"\x89PNG\r\n\x1a\n";

fn is_valid_texture_name(name: &str) -> bool {
    (1..=48).contains(&name.len())
        && name
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_')
}

const BLOCK_TEXTURE_DIR: &str = "assets/minecraft/textures/block/";
const PROBE_TEXTURE: &str = "assets/minecraft/textures/block/stone.png";

static TEXTURE_INDEX: Mutex<Option<(PathBuf, Vec<String>)>> = Mutex::const_new(None);

fn client_jars_newest_first(versions_dir: &Path) -> Vec<PathBuf> {
    let Ok(entries) = std::fs::read_dir(versions_dir) else {
        return Vec::new();
    };
    let mut jars: Vec<(SystemTime, PathBuf)> = entries
        .flatten()
        .filter_map(|entry| {
            let id = entry.file_name().to_string_lossy().to_string();
            let jar = entry.path().join(format!("{id}.jar"));
            let metadata = std::fs::metadata(&jar).ok()?;
            metadata
                .is_file()
                .then(|| (metadata.modified().unwrap_or(SystemTime::UNIX_EPOCH), jar))
        })
        .collect();
    jars.sort_by_key(|(modified, _)| std::cmp::Reverse(*modified));
    jars.into_iter().map(|(_, jar)| jar).collect()
}

fn has_block_textures(jar: &Path) -> bool {
    std::fs::File::open(jar)
        .ok()
        .and_then(|file| zip::ZipArchive::new(file).ok())
        .is_some_and(|mut archive| archive.by_name(PROBE_TEXTURE).is_ok())
}

fn newest_client_jar(versions_dir: &Path) -> Option<PathBuf> {
    client_jars_newest_first(versions_dir)
        .into_iter()
        .find(|jar| has_block_textures(jar))
}

fn texture_names(jar: &Path) -> Result<Vec<String>, String> {
    let file = std::fs::File::open(jar).map_err(|e| e.to_string())?;
    let archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    let mut names: Vec<String> = archive
        .file_names()
        .filter_map(|entry| entry.strip_prefix(BLOCK_TEXTURE_DIR))
        .filter_map(|rest| rest.strip_suffix(".png"))
        .filter(|name| is_valid_texture_name(name))
        .map(str::to_string)
        .collect();
    names.sort_unstable();
    names.dedup();
    Ok(names)
}

fn extract_missing(jar: &Path, cache_dir: &Path, names: &[String]) -> Result<(), String> {
    let file = std::fs::File::open(jar).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(cache_dir).map_err(|e| e.to_string())?;
    for name in names {
        let entry_name = format!("{BLOCK_TEXTURE_DIR}{name}.png");
        let Ok(mut entry) = archive.by_name(&entry_name) else {
            continue;
        };
        if entry.size() > MAX_TEXTURE_BYTES {
            continue;
        }
        let mut bytes = Vec::with_capacity(entry.size() as usize);
        if (&mut entry)
            .take(MAX_TEXTURE_BYTES)
            .read_to_end(&mut bytes)
            .is_err()
            || !bytes.starts_with(PNG_SIGNATURE)
        {
            continue;
        }
        let _ = std::fs::write(cache_dir.join(format!("{name}.png")), &bytes);
    }
    Ok(())
}

fn load_textures(caches_dir: PathBuf, names: Vec<String>) -> HashMap<String, String> {
    let cache_dir = caches_dir.join("block-textures");
    let missing: Vec<String> = names
        .iter()
        .filter(|name| !cache_dir.join(format!("{name}.png")).is_file())
        .cloned()
        .collect();
    if !missing.is_empty() {
        for jar in client_jars_newest_first(&caches_dir.join("versions")) {
            if let Err(err) = extract_missing(&jar, &cache_dir, &missing) {
                tracing::debug!(
                    "could not read block textures from {}: {err}",
                    jar.display()
                );
            }
            if missing
                .iter()
                .all(|name| cache_dir.join(format!("{name}.png")).is_file())
            {
                break;
            }
        }
    }
    let mut result = HashMap::new();
    for name in names {
        let path = cache_dir.join(format!("{name}.png"));
        let Ok(bytes) = std::fs::read(&path) else {
            continue;
        };
        if bytes.len() as u64 <= MAX_TEXTURE_BYTES && bytes.starts_with(PNG_SIGNATURE) {
            result.insert(
                name,
                format!("data:image/png;base64,{}", STANDARD.encode(bytes)),
            );
        }
    }
    result
}

async fn ensure_client_jar(state: &AppState) -> Option<PathBuf> {
    let versions_dir = state.caches_dir.join("versions");
    if let Some(jar) = newest_client_jar(&versions_dir) {
        return Some(jar);
    }
    let _guard = JAR_DOWNLOAD.lock().await;
    if let Some(jar) = newest_client_jar(&versions_dir) {
        return Some(jar);
    }
    let manifest = state.resolver.fetch_manifest().await.ok()?;
    let version = manifest.latest.release;
    let details = state.resolver.get_version_details(&version).await.ok()?;
    let download = details.downloads.client;
    if download.url.is_empty() || download.sha1.is_empty() {
        return None;
    }
    let dest = versions_dir.join(&version).join(format!("{version}.jar"));
    let client = util::http_client().ok()?;
    let expected = ExpectedHash {
        sha1: Some(download.sha1.as_str()),
        sha512: None,
    };
    match util::download_verified(&client, &download.url, &dest, &expected).await {
        Ok(_) => Some(dest),
        Err(err) => {
            tracing::warn!("could not download Minecraft {version} for block textures: {err}");
            None
        }
    }
}

#[tauri::command]
pub async fn get_block_textures(
    state: State<'_, AppState>,
    names: Vec<String>,
) -> Result<HashMap<String, String>, String> {
    if names.len() > MAX_TEXTURES {
        return Err("Too many textures requested.".to_string());
    }
    if let Some(bad) = names.iter().find(|name| !is_valid_texture_name(name)) {
        return Err(format!("Invalid texture name: {bad}"));
    }
    let cache_dir = state.caches_dir.join("block-textures");
    let all_cached = names
        .iter()
        .all(|name| cache_dir.join(format!("{name}.png")).is_file());
    if !all_cached {
        ensure_client_jar(&state).await;
    }
    let caches_dir = state.caches_dir.clone();
    tokio::task::spawn_blocking(move || load_textures(caches_dir, names))
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn list_block_textures(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let Some(jar) = ensure_client_jar(&state).await else {
        return Ok(Vec::new());
    };
    let mut index = TEXTURE_INDEX.lock().await;
    if let Some((indexed_jar, names)) = index.as_ref() {
        if *indexed_jar == jar {
            return Ok(names.clone());
        }
    }
    let source = jar.clone();
    let names = tokio::task::spawn_blocking(move || texture_names(&source))
        .await
        .map_err(|e| e.to_string())??;
    *index = Some((jar, names.clone()));
    Ok(names)
}
