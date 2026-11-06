use reqwest::Client;
use std::collections::HashSet;
use std::path::PathBuf;

use crate::error::LaunchError;
use crate::models::{VersionDetails, VersionManifest};

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Clone)]
pub struct VersionResolver {
    http: Client,
    base_dir: PathBuf,
}

impl VersionResolver {
    pub fn new(base_dir: PathBuf) -> Self {
        let http = crate::download::http_client().unwrap_or_else(|_| Client::new());
        Self { http, base_dir }
    }

    pub fn is_valid_version_id(version_id: &str) -> bool {
        !version_id.is_empty()
            && version_id.len() <= 64
            && version_id != "."
            && version_id != ".."
            && version_id
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_' | '+' | ' '))
    }

    pub async fn fetch_manifest(&self) -> Result<VersionManifest, LaunchError> {
        let cache_path = self.base_dir.join("version_manifest_v2.json");
        let fetched = async {
            let response = self
                .http
                .get(MANIFEST_URL)
                .send()
                .await
                .map_err(|e| LaunchError::Network(e.to_string()))?;
            if !response.status().is_success() {
                return Err(LaunchError::Network(format!(
                    "version manifest returned {}",
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
                let _ = tokio::fs::create_dir_all(&self.base_dir).await;
                let _ = tokio::fs::write(&cache_path, &text).await;
                text
            }
            Err(err) => tokio::fs::read_to_string(&cache_path)
                .await
                .map_err(|_| err)?,
        };

        serde_json::from_str(&text).map_err(|e| LaunchError::Parse(e.to_string()))
    }

    pub async fn version_exists(&self, version_id: &str) -> Result<bool, LaunchError> {
        let manifest = self.fetch_manifest().await?;
        Ok(manifest.versions.iter().any(|v| v.id == version_id))
    }

    pub async fn first_valid_version<'a>(
        &self,
        preferred: &'a str,
        candidates: &'a [String],
    ) -> Result<Option<String>, LaunchError> {
        let manifest = self.fetch_manifest().await?;
        let ids: HashSet<&str> = manifest.versions.iter().map(|v| v.id.as_str()).collect();

        if ids.contains(preferred) {
            return Ok(Some(preferred.to_string()));
        }

        for candidate in candidates {
            if ids.contains(candidate.as_str()) {
                return Ok(Some(candidate.clone()));
            }
        }

        Ok(None)
    }

    pub async fn get_version_details(
        &self,
        version_id: &str,
    ) -> Result<VersionDetails, LaunchError> {
        if !Self::is_valid_version_id(version_id) {
            return Err(LaunchError::NotFound(format!(
                "invalid version id '{version_id}'"
            )));
        }
        let version_dir = self.base_dir.join("versions").join(version_id);
        std::fs::create_dir_all(&version_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let json_path = version_dir.join(format!("{version_id}.json"));

        if json_path.exists() {
            let content = tokio::fs::read_to_string(&json_path)
                .await
                .map_err(|e| LaunchError::Io(e.to_string()))?;

            let details: VersionDetails =
                serde_json::from_str(&content).map_err(|e| LaunchError::Parse(e.to_string()))?;

            return Ok(details);
        }

        let manifest = self.fetch_manifest().await?;
        let version_entry = manifest
            .versions
            .iter()
            .find(|v| v.id == version_id)
            .ok_or_else(|| LaunchError::NotFound(format!("version {version_id}")))?;

        let text = self
            .http
            .get(&version_entry.url)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?
            .text()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;

        if !version_entry.sha1.is_empty() {
            use sha1::{Digest, Sha1};
            let actual = format!("{:x}", Sha1::digest(text.as_bytes()));
            if !actual.eq_ignore_ascii_case(&version_entry.sha1) {
                return Err(LaunchError::Integrity(format!(
                    "version JSON for {version_id} failed verification"
                )));
            }
        }

        let details: VersionDetails =
            serde_json::from_str(&text).map_err(|e| LaunchError::Parse(e.to_string()))?;

        tokio::fs::write(&json_path, &text)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        Ok(details)
    }
}
