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

fn newest_client_jar(versions_dir: &Path) -> Option<PathBuf> {
    let entries = std::fs::read_dir(versions_dir).ok()?;
    let mut best: Option<(SystemTime, PathBuf)> = None;
    for entry in entries.flatten() {
        let id = entry.file_name().to_string_lossy().to_string();
        let jar = entry.path().join(format!("{id}.jar"));
        let Ok(metadata) = std::fs::metadata(&jar) else {
            continue;
        };
        if !metadata.is_file() {
            continue;
        }
        let modified = metadata.modified().unwrap_or(SystemTime::UNIX_EPOCH);
        let newer = match &best {
            Some((time, _)) => modified > *time,
            None => true,
        };
        if newer {
            best = Some((modified, jar));
        }
    }
    best.map(|(_, jar)| jar)
}

fn extract_missing(jar: &Path, cache_dir: &Path, names: &[String]) -> Result<(), String> {
    let file = std::fs::File::open(jar).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(cache_dir).map_err(|e| e.to_string())?;
    for name in names {
        let entry_name = format!("assets/minecraft/textures/block/{name}.png");
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
        if let Some(jar) = newest_client_jar(&caches_dir.join("versions")) {
            if let Err(err) = extract_missing(&jar, &cache_dir, &missing) {
                tracing::debug!(
                    "could not read block textures from {}: {err}",
                    jar.display()
                );
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
