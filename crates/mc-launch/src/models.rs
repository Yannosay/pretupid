use serde::{Deserialize, Serialize};

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
}

impl Default for LaunchSettings {
    fn default() -> Self {
        Self {
            memory_min_mb: 512,
            memory_max_mb: 4096,
            resolution_width: 1280,
            resolution_height: 720,
            fullscreen: false,
            java_path: None,
            auto_install_java: true,
            extra_jvm_args: Vec::new(),
            extra_game_args: Vec::new(),
        }
    }
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
pub struct VersionEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    pub url: String,
    pub time: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
    pub sha1: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionDetails {
    pub id: String,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    #[serde(rename = "minecraftArguments", default)]
    pub minecraft_arguments: Option<String>,
    #[serde(rename = "assetIndex")]
    pub asset_index: AssetIndex,
    pub assets: String,
    pub downloads: Downloads,
    pub libraries: Vec<Library>,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    #[serde(rename = "minimumLauncherVersion")]
    pub minimum_launcher_version: u32,
    #[serde(rename = "javaVersion", default)]
    pub java_version: Option<JavaVersion>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Arguments {
    pub game: Vec<serde_json::Value>,
    pub jvm: Vec<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetIndex {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
    #[serde(rename = "totalSize")]
    pub total_size: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Downloads {
    pub client: DownloadEntry,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownloadEntry {
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    pub downloads: LibraryDownloads,
    #[serde(default)]
    pub natives: Option<std::collections::HashMap<String, String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<Artifact>,
    #[serde(default)]
    pub classifiers: Option<std::collections::HashMap<String, Artifact>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Artifact {
    pub path: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JavaVersion {
    pub component: String,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetIndexDetails {
    pub objects: std::collections::HashMap<String, AssetObject>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}