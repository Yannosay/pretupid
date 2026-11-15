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
    #[serde(default)]
    pub last_played: Option<chrono::DateTime<chrono::Utc>>,
    #[serde(default)]
    pub playtime_seconds: i64,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub group_name: Option<String>,
    #[serde(default)]
    pub icon_style: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ModLoader {
    #[default]
    Vanilla,
    Forge,
    Fabric,
    Quilt,
    NeoForge,
    Modrinth,
}

impl ModLoader {
    pub fn as_str(&self) -> &'static str {
        match self {
            ModLoader::Vanilla => "vanilla",
            ModLoader::Forge => "forge",
            ModLoader::Fabric => "fabric",
            ModLoader::Quilt => "quilt",
            ModLoader::NeoForge => "neoforge",
            ModLoader::Modrinth => "modrinth",
        }
    }

    pub fn parse(value: &str) -> Self {
        match value {
            "forge" => ModLoader::Forge,
            "fabric" => ModLoader::Fabric,
            "quilt" => ModLoader::Quilt,
            "neoforge" => ModLoader::NeoForge,
            "modrinth" => ModLoader::Modrinth,
            _ => ModLoader::Vanilla,
        }
    }

    pub fn compatible_mod_loaders(&self, minecraft_version: &str) -> Vec<&'static str> {
        match self {
            ModLoader::Vanilla => vec![],
            ModLoader::Fabric | ModLoader::Modrinth => vec!["fabric"],
            ModLoader::Quilt => vec!["quilt", "fabric"],
            ModLoader::Forge => vec!["forge"],
            ModLoader::NeoForge if minecraft_version == "1.20.1" => vec!["neoforge", "forge"],
            ModLoader::NeoForge => vec!["neoforge"],
        }
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

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct UpdateInstanceRequest {
    pub name: Option<String>,
    pub description: Option<String>,
    pub minecraft_version: Option<String>,
    pub mod_loader: Option<ModLoader>,
    pub loader_version: Option<String>,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub group_name: Option<String>,
    #[serde(default)]
    pub icon_style: Option<String>,
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
    #[serde(default)]
    pub quick_play_server: Option<String>,
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
            quick_play_server: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct InstanceMod {
    pub id: String,
    pub instance_id: String,
    pub project_id: String,
    pub project_type: String,
    pub title: String,
    pub icon_url: Option<String>,
    pub version_id: Option<String>,
    pub version_name: Option<String>,
    pub file_url: Option<String>,
    pub file_name: Option<String>,
    pub created_at: String,
    #[sqlx(default)]
    pub enabled: bool,
    #[sqlx(default)]
    pub sha1: Option<String>,
}

#[derive(Debug, Clone, Default)]
pub struct NewInstanceMod {
    pub instance_id: String,
    pub project_id: String,
    pub project_type: String,
    pub title: String,
    pub icon_url: Option<String>,
    pub version_id: Option<String>,
    pub version_name: Option<String>,
    pub file_url: Option<String>,
    pub file_name: Option<String>,
    pub sha1: Option<String>,
    pub enabled: bool,
}

pub fn content_folder(project_type: &str) -> Option<&'static str> {
    match project_type {
        "mod" => Some("mods"),
        "resourcepack" => Some("resourcepacks"),
        "shader" | "shaderpack" => Some("shaderpacks"),
        _ => None,
    }
}

pub fn is_safe_file_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 255
        && name != "."
        && name != ".."
        && !name.contains(['/', '\\', ':', '*', '?', '"', '<', '>', '|', '\0'])
        && !name.ends_with('.')
        && !name.ends_with(' ')
}
