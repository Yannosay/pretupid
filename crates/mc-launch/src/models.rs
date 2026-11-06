use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionEntry {
    pub id: String,
    pub r#type: String,
    pub url: String,
    pub time: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
    pub sha1: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionManifest {
    pub latest: LatestVersions,
    pub versions: Vec<VersionEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LatestVersions {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionDetails {
    pub id: String,
    #[serde(default)]
    pub assets: String,
    #[serde(rename = "assetIndex", default)]
    pub asset_index: AssetIndex,
    #[serde(default)]
    pub downloads: Downloads,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(rename = "mainClass", default)]
    pub main_class: String,
    #[serde(rename = "javaVersion", default)]
    pub java_version: Option<JavaVersion>,
    #[serde(rename = "type", default = "default_version_type")]
    pub version_type: String,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    #[serde(rename = "minecraftArguments", default)]
    pub minecraft_arguments: Option<String>,
    #[serde(rename = "inheritsFrom", default)]
    pub inherits_from: Option<String>,
}

fn default_version_type() -> String {
    "release".to_string()
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<serde_json::Value>,
    #[serde(default)]
    pub jvm: Vec<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct OsRule {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AssetIndex {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub sha1: String,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub url: String,
    #[serde(rename = "totalSize", default)]
    pub total_size: u64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Downloads {
    #[serde(default)]
    pub client: DownloadEntry,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct DownloadEntry {
    #[serde(default)]
    pub sha1: String,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: LibraryDownloads,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<u64>,
    #[serde(default)]
    pub natives: Option<HashMap<String, String>>,
    #[serde(default)]
    pub rules: Option<Vec<Rule>>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct LibraryDownloads {
    #[serde(default)]
    pub artifact: Option<Artifact>,
    #[serde(default)]
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Artifact {
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub sha1: String,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JavaVersion {
    #[serde(default)]
    pub component: String,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetIndexDetails {
    pub objects: HashMap<String, AssetObject>,
    #[serde(default)]
    pub map_to_resources: bool,
    #[serde(rename = "virtual", default)]
    pub is_virtual: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LaunchSettings {
    pub memory_min_mb: u32,
    pub memory_max_mb: u32,
    pub resolution_width: u32,
    pub resolution_height: u32,
    pub fullscreen: bool,
    pub java_path: Option<String>,
    pub auto_install_java: bool,
    pub extra_jvm_args: Vec<String>,
    pub extra_game_args: Vec<String>,
    #[serde(default)]
    pub verify_files: bool,
    #[serde(default)]
    pub quick_play_server: Option<String>,
}

#[derive(Debug, Clone)]
pub struct LaunchAccount {
    pub username: String,
    pub uuid: String,
    pub access_token: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LoaderKind {
    Vanilla,
    Fabric,
    Quilt,
    Forge,
    NeoForge,
}

impl LoaderKind {
    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "vanilla" => Some(Self::Vanilla),
            "fabric" | "modrinth" => Some(Self::Fabric),
            "quilt" => Some(Self::Quilt),
            "forge" => Some(Self::Forge),
            "neoforge" => Some(Self::NeoForge),
            _ => None,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::Vanilla => "Vanilla",
            Self::Fabric => "Fabric",
            Self::Quilt => "Quilt",
            Self::Forge => "Forge",
            Self::NeoForge => "NeoForge",
        }
    }
}

#[derive(Debug, Clone)]
pub struct LaunchRequest {
    pub profile_name: String,
    pub version_id: String,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
    pub settings: LaunchSettings,
    pub account: LaunchAccount,
}
