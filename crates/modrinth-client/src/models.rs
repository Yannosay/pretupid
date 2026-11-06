use serde::{Deserialize, Deserializer, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct SearchResult {
    pub project_id: String,
    pub project_type: ProjectType,
    pub slug: String,
    pub author: String,
    pub title: String,
    pub description: String,
    pub categories: Vec<String>,
    pub display_categories: Vec<String>,
    pub versions: Vec<String>,
    pub downloads: u64,
    pub follows: u64,
    pub icon_url: Option<String>,
    pub date_created: String,
    pub date_modified: String,
    pub latest_version: Option<String>,
    #[serde(default, deserialize_with = "deserialize_string_or_object")]
    pub license: String,
    pub client_side: Side,
    pub server_side: Side,
    pub gallery: Vec<String>,
    pub featured_gallery: Option<String>,
    pub color: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct SearchResponse {
    pub hits: Vec<SearchResult>,
    pub offset: u32,
    pub limit: u32,
    pub total_hits: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ProjectType {
    Mod,
    Modpack,
    ResourcePack,
    Shader,
    #[serde(other)]
    Unknown,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Side {
    Required,
    Optional,
    Unsupported,
    Unknown,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Project {
    pub id: String,
    pub slug: Option<String>,
    pub project_type: ProjectType,
    #[serde(default, deserialize_with = "deserialize_string_or_object")]
    pub team: String,
    pub title: String,
    pub description: String,
    pub body: String,
    pub published: String,
    pub updated: String,
    pub approved: Option<String>,
    pub status: ProjectStatus,
    pub moderator_message: Option<String>,
    #[serde(default, deserialize_with = "deserialize_license")]
    pub license: Option<License>,
    pub downloads: u64,
    pub followers: u64,
    pub categories: Vec<String>,
    pub additional_categories: Vec<String>,
    pub versions: Vec<String>,
    pub icon_url: Option<String>,
    pub issues_url: Option<String>,
    pub source_url: Option<String>,
    pub wiki_url: Option<String>,
    pub discord_url: Option<String>,
    #[serde(default, deserialize_with = "deserialize_donation_urls")]
    pub donation_urls: Vec<DonationUrl>,
    pub gallery: Vec<String>,
    pub featured_gallery: Option<String>,
    pub color: Option<u32>,
    pub client_side: Side,
    pub server_side: Side,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ProjectStatus {
    Approved,
    Rejected,
    Draft,
    Unlisted,
    Archived,
    Processing,
    Withheld,
    Scheduled,
    Private,
    Unknown,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct License {
    pub id: String,
    pub name: String,
    pub url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct DonationUrl {
    pub id: String,
    pub platform: String,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Version {
    pub id: String,
    pub project_id: String,
    pub author_id: String,
    pub featured: bool,
    pub name: String,
    pub version_number: String,
    pub changelog: Option<String>,
    pub changelog_url: Option<String>,
    pub date_published: String,
    pub downloads: u64,
    pub version_type: VersionType,
    pub status: VersionStatus,
    pub requested_status: Option<VersionStatus>,
    pub files: Vec<VersionFile>,
    pub dependencies: Vec<Dependency>,
    pub game_versions: Vec<String>,
    pub loaders: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum VersionType {
    Release,
    Beta,
    Alpha,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum VersionStatus {
    Listed,
    Archived,
    Draft,
    Unlisted,
    Scheduled,
    Unknown,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct VersionFile {
    pub hashes: FileHashes,
    pub url: String,
    pub filename: String,
    pub primary: bool,
    pub size: u64,
    pub file_type: Option<FileType>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct FileHashes {
    pub sha1: String,
    pub sha512: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum FileType {
    RequiredResourcePack,
    OptionalResourcePack,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Dependency {
    pub version_id: Option<String>,
    pub project_id: Option<String>,
    pub file_name: Option<String>,
    pub dependency_type: DependencyType,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum DependencyType {
    Required,
    Optional,
    Incompatible,
    Embedded,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Category {
    pub name: String,
    pub project_type: ProjectType,
    pub icon: String,
    pub header: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Loader {
    pub name: String,
    pub icon: String,
    pub supported_project_types: Vec<ProjectType>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct GameVersion {
    pub version: String,
    pub version_type: GameVersionType,
    pub date: String,
    pub major: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum GameVersionType {
    Release,
    Snapshot,
    Alpha,
    Beta,
    PreRelease,
    #[serde(other)]
    Other,
}

fn deserialize_string_or_object<'de, D>(deserializer: D) -> Result<String, D::Error>
where
    D: Deserializer<'de>,
{
    let value = Value::deserialize(deserializer)?;
    match value {
        Value::String(s) => Ok(s),
        Value::Object(map) => {
            if let Some(id) = map.get("id").and_then(Value::as_str) {
                Ok(id.to_string())
            } else if let Some(name) = map.get("name").and_then(Value::as_str) {
                Ok(name.to_string())
            } else {
                Ok(String::new())
            }
        }
        _ => Ok(String::new()),
    }
}

fn deserialize_license<'de, D>(deserializer: D) -> Result<Option<License>, D::Error>
where
    D: Deserializer<'de>,
{
    let value = Value::deserialize(deserializer)?;
    match value {
        Value::Null => Ok(None),
        Value::String(id) => Ok(Some(License {
            id,
            name: String::new(),
            url: None,
        })),
        Value::Object(map) => Ok(Some(License {
            id: map
                .get("id")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            name: map
                .get("name")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            url: map.get("url").and_then(Value::as_str).map(String::from),
        })),
        _ => Ok(None),
    }
}

fn deserialize_donation_urls<'de, D>(deserializer: D) -> Result<Vec<DonationUrl>, D::Error>
where
    D: Deserializer<'de>,
{
    let value = Value::deserialize(deserializer)?;
    match value {
        Value::Null => Ok(Vec::new()),
        Value::Array(arr) => {
            let mut result = Vec::new();
            for item in arr {
                match item {
                    Value::String(s) => result.push(DonationUrl {
                        id: String::new(),
                        platform: s,
                        url: String::new(),
                    }),
                    Value::Object(map) => result.push(DonationUrl {
                        id: map
                            .get("id")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string(),
                        platform: map
                            .get("platform")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string(),
                        url: map
                            .get("url")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string(),
                    }),
                    _ => {}
                }
            }
            Ok(result)
        }
        _ => Ok(Vec::new()),
    }
}
