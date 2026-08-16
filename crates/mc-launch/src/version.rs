use reqwest::Client;
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
        Self {
            http: Client::new(),
            base_dir,
        }
    }

    pub async fn fetch_manifest(&self) -> Result<VersionManifest, LaunchError> {
        let response = self
            .http
            .get(MANIFEST_URL)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?
            .json::<VersionManifest>()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;

        Ok(response)
    }

    pub async fn get_version_details(
        &self,
        version_id: &str,
    ) -> Result<VersionDetails, LaunchError> {
        let version_dir = self.base_dir.join("versions").join(version_id);
        std::fs::create_dir_all(&version_dir)
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        let json_path = version_dir.join(format!("{version_id}.json"));

        if json_path.exists() {
            let content = tokio::fs::read_to_string(&json_path)
                .await
                .map_err(|e| LaunchError::Io(e.to_string()))?;

            let details: VersionDetails = serde_json::from_str(&content)
                .map_err(|e| LaunchError::Parse(e.to_string()))?;

            return Ok(details);
        }

        let manifest = self.fetch_manifest().await?;
        let version_entry = manifest
            .versions
            .iter()
            .find(|v| v.id == version_id)
            .ok_or_else(|| LaunchError::NotFound(format!("version {version_id}")))?;

        let details = self
            .http
            .get(&version_entry.url)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?
            .json::<VersionDetails>()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;

        let json = serde_json::to_string_pretty(&details)
            .map_err(|e| LaunchError::Parse(e.to_string()))?;

        tokio::fs::write(&json_path, json)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        Ok(details)
    }
}