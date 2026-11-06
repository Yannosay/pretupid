use std::path::Path;

use crate::error::{LaunchError, LaunchResult};
use crate::models::{LoaderKind, VersionDetails};

use super::LoaderVersion;

fn meta_base(kind: LoaderKind) -> &'static str {
    match kind {
        LoaderKind::Quilt => "https://meta.quiltmc.org/v3",
        _ => "https://meta.fabricmc.net/v2",
    }
}

fn encode(segment: &str) -> String {
    url::form_urlencoded::byte_serialize(segment.as_bytes()).collect()
}

pub async fn list_versions(
    client: &reqwest::Client,
    kind: LoaderKind,
    mc_version: &str,
) -> LaunchResult<Vec<LoaderVersion>> {
    let url = format!("{}/versions/loader/{}", meta_base(kind), encode(mc_version));
    let response = client
        .get(&url)
        .send()
        .await
        .map_err(|e| LaunchError::Network(e.to_string()))?;
    if response.status() == reqwest::StatusCode::NOT_FOUND
        || response.status() == reqwest::StatusCode::BAD_REQUEST
    {
        return Ok(Vec::new());
    }
    if !response.status().is_success() {
        return Err(LaunchError::Network(format!(
            "{} loader list failed: {}",
            kind.label(),
            response.status()
        )));
    }
    let entries: Vec<serde_json::Value> = response
        .json()
        .await
        .map_err(|e| LaunchError::Parse(e.to_string()))?;

    let mut versions: Vec<LoaderVersion> = entries
        .iter()
        .filter_map(|entry| {
            let loader = entry.get("loader")?;
            let version = loader.get("version")?.as_str()?.to_string();
            let stable = match loader.get("stable").and_then(|s| s.as_bool()) {
                Some(s) => s,
                None => {
                    !version.contains("beta") && !version.contains("pre") && !version.contains("rc")
                }
            };
            Some(LoaderVersion {
                version,
                stable,
                recommended: false,
                latest: false,
            })
        })
        .collect();

    if let Some(first) = versions.first_mut() {
        first.latest = true;
    }
    if let Some(rec) = versions.iter_mut().find(|v| v.stable) {
        rec.recommended = true;
    }
    Ok(versions)
}

pub async fn resolve_profile(
    client: &reqwest::Client,
    kind: LoaderKind,
    mc_version: &str,
    requested: Option<&str>,
    caches_dir: &Path,
) -> LaunchResult<VersionDetails> {
    let requested = requested
        .map(str::trim)
        .filter(|v| !v.is_empty() && *v != "latest");
    let cache_dir = caches_dir.join("loaders");
    tokio::fs::create_dir_all(&cache_dir).await?;
    let slug = kind.label().to_lowercase();

    let version = match requested {
        Some(v) => v.to_string(),
        None => match list_versions(client, kind, mc_version).await {
            Ok(list) => list
                .iter()
                .find(|v| v.recommended)
                .or_else(|| list.first())
                .map(|v| v.version.clone())
                .ok_or_else(|| {
                    LaunchError::NotFound(format!(
                        "{} does not support Minecraft {}",
                        kind.label(),
                        mc_version
                    ))
                })?,
            Err(err) => {
                let fallback = find_cached_profile(&cache_dir, &slug, mc_version).await;
                return match fallback {
                    Some(details) => Ok(details),
                    None => Err(err),
                };
            }
        },
    };

    let cache_path = cache_dir.join(format!("{}-{}-{}.json", slug, mc_version, version));
    let url = format!(
        "{}/versions/loader/{}/{}/profile/json",
        meta_base(kind),
        encode(mc_version),
        encode(&version)
    );

    let fetched = async {
        let response = client
            .get(&url)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "{} profile for {} / {} is unavailable ({})",
                kind.label(),
                mc_version,
                version,
                response.status()
            )));
        }
        response
            .text()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))
    }
    .await;

    let text = match fetched {
        Ok(text) => {
            let _ = tokio::fs::write(&cache_path, &text).await;
            text
        }
        Err(err) => match tokio::fs::read_to_string(&cache_path).await {
            Ok(text) => text,
            Err(_) => return Err(err),
        },
    };

    serde_json::from_str(&text)
        .map_err(|e| LaunchError::Parse(format!("{} profile: {}", kind.label(), e)))
}

async fn find_cached_profile(
    cache_dir: &Path,
    slug: &str,
    mc_version: &str,
) -> Option<VersionDetails> {
    let prefix = format!("{}-{}-", slug, mc_version);
    let mut entries = tokio::fs::read_dir(cache_dir).await.ok()?;
    let mut newest: Option<(std::time::SystemTime, std::path::PathBuf)> = None;
    while let Ok(Some(entry)) = entries.next_entry().await {
        let name = entry.file_name().to_string_lossy().to_string();
        if !name.starts_with(&prefix) || !name.ends_with(".json") {
            continue;
        }
        let modified = entry.metadata().await.ok()?.modified().ok()?;
        if newest.as_ref().map(|(t, _)| modified > *t).unwrap_or(true) {
            newest = Some((modified, entry.path()));
        }
    }
    let (_, path) = newest?;
    let text = tokio::fs::read_to_string(path).await.ok()?;
    serde_json::from_str(&text).ok()
}
