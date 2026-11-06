pub mod fabric;
pub mod forge;

use serde::{Deserialize, Serialize};

use crate::error::LaunchResult;
use crate::models::LoaderKind;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoaderVersion {
    pub version: String,
    pub stable: bool,
    pub recommended: bool,
    pub latest: bool,
}

pub async fn list_loader_versions(
    client: &reqwest::Client,
    kind: LoaderKind,
    mc_version: &str,
) -> LaunchResult<Vec<LoaderVersion>> {
    match kind {
        LoaderKind::Vanilla => Ok(Vec::new()),
        LoaderKind::Fabric | LoaderKind::Quilt => {
            fabric::list_versions(client, kind, mc_version).await
        }
        LoaderKind::Forge => forge::list_forge_versions(client, mc_version).await,
        LoaderKind::NeoForge => forge::list_neoforge_versions(client, mc_version).await,
    }
}
