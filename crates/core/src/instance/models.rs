use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Instance {
    pub id: Uuid,
    pub name: String,
    pub description: String,
    pub minecraft_version: String,
    pub mod_loader: ModLoader,
    pub loader_version: Option<String>,
    pub folder_name: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
    pub updated_at: chrono::DateTime<chrono::Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ModLoader {
    Vanilla,
    Forge,
    Fabric,
    Quilt,
}

impl Default for ModLoader {
    fn default() -> Self {
        Self::Vanilla
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateInstanceRequest {
    pub name: String,
    pub description: String,
    pub minecraft_version: String,
    pub mod_loader: ModLoader,
    pub loader_version: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateInstanceRequest {
    pub name: Option<String>,
    pub description: Option<String>,
    pub minecraft_version: Option<String>,
    pub mod_loader: Option<ModLoader>,
    pub loader_version: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InstanceSettings {
    pub memory_min_mb: u32,
    pub memory_max_mb: u32,
    pub resolution_width: u32,
    pub resolution_height: u32,
    pub fullscreen: bool,
    pub java_path: Option<String>,
    pub extra_jvm_args: Vec<String>,
    pub extra_game_args: Vec<String>,
}

impl Default for InstanceSettings {
    fn default() -> Self {
        Self {
            memory_min_mb: 512,
            memory_max_mb: 4096,
            resolution_width: 1280,
            resolution_height: 720,
            fullscreen: false,
            java_path: None,
            extra_jvm_args: Vec::new(),
            extra_game_args: Vec::new(),
        }
    }
}