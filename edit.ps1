$ErrorActionPreference = "Stop"
$root = 'C:\Users\Yannosay\Desktop\pretupid-launcher'
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$crateDir = "$root\crates\modrinth-client"
$srcDir = "$crateDir\src"

New-Item -ItemType Directory -Force -Path $srcDir | Out-Null

$cargoToml = @'
[package]
name = "modrinth-client"
version = "0.1.0"
edition = "2021"

[dependencies]
reqwest = { version = "0.12", features = ["json", "rustls-tls"], default-features = false }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
thiserror = "2"
'@

[System.IO.File]::WriteAllText("$crateDir\Cargo.toml", $cargoToml, $utf8NoBom)

$libRs = @'
pub mod client;
pub mod error;
pub mod models;

pub use client::ModrinthClient;
pub use error::Error;
'@

[System.IO.File]::WriteAllText("$srcDir\lib.rs", $libRs, $utf8NoBom)

$errorRs = @'
use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error("HTTP request failed: {0}")]
    Http(#[from] reqwest::Error),
    #[error("JSON serialization/deserialization failed: {0}")]
    Json(#[from] serde_json::Error),
    #[error("API error: {0}")]
    Api(String),
}
'@

[System.IO.File]::WriteAllText("$srcDir\error.rs", $errorRs, $utf8NoBom)

$modelsRs = @'
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
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
    pub license: String,
    pub client_side: Side,
    pub server_side: Side,
    pub gallery: Vec<String>,
    pub featured_gallery: Option<String>,
    pub color: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
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
#[serde(rename_all = "camelCase")]
pub struct Project {
    pub id: String,
    pub slug: Option<String>,
    pub project_type: ProjectType,
    pub team: String,
    pub title: String,
    pub description: String,
    pub body: String,
    pub published: String,
    pub updated: String,
    pub approved: Option<String>,
    pub status: ProjectStatus,
    pub moderator_message: Option<String>,
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
#[serde(rename_all = "camelCase")]
pub struct License {
    pub id: String,
    pub name: String,
    pub url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DonationUrl {
    pub id: String,
    pub platform: String,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
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
#[serde(rename_all = "camelCase")]
pub struct VersionFile {
    pub hashes: FileHashes,
    pub url: String,
    pub filename: String,
    pub primary: bool,
    pub size: u64,
    pub file_type: Option<FileType>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
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
#[serde(rename_all = "camelCase")]
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
#[serde(rename_all = "camelCase")]
pub struct Category {
    pub name: String,
    pub project_type: ProjectType,
    pub icon: String,
    pub header: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Loader {
    pub name: String,
    pub icon: String,
    pub supported_project_types: Vec<ProjectType>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
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
'@

[System.IO.File]::WriteAllText("$srcDir\models.rs", $modelsRs, $utf8NoBom)

$clientRs = @'
use crate::error::Error;
use crate::models::*;
use reqwest::header::{HeaderMap, HeaderValue, ACCEPT, USER_AGENT};
use serde::de::DeserializeOwned;
use std::time::Duration;

const DEFAULT_BASE_URL: &str = "https://api.modrinth.com/v2";
const DEFAULT_USER_AGENT: &str = "PretupidLauncher/0.0.1 (contact@yannosay.com)";

#[derive(Debug, Clone)]
pub struct ModrinthClient {
    http: reqwest::Client,
    base_url: String,
}

impl ModrinthClient {
    pub fn new() -> Result<Self, Error> {
        Self::with_base_url(DEFAULT_BASE_URL)
    }

    pub fn with_base_url(base_url: impl Into<String>) -> Result<Self, Error> {
        let mut headers = HeaderMap::new();
        headers.insert(ACCEPT, HeaderValue::from_static("application/json"));
        headers.insert(USER_AGENT, HeaderValue::from_static(DEFAULT_USER_AGENT));

        let http = reqwest::Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(30))
            .build()?;

        Ok(Self {
            http,
            base_url: base_url.into(),
        })
    }

    pub async fn search_projects(
        &self,
        query: &str,
        facets: Option<&str>,
        index: Option<&str>,
        offset: Option<u32>,
        limit: Option<u32>,
    ) -> Result<SearchResponse, Error> {
        let url = format!("{}/search", self.base_url);
        let mut request = self.http.get(&url).query(&[("query", query)]);

        if let Some(facets) = facets {
            request = request.query(&[("facets", facets)]);
        }
        if let Some(index) = index {
            request = request.query(&[("index", index)]);
        }
        if let Some(offset) = offset {
            request = request.query(&[("offset", offset)]);
        }
        if let Some(limit) = limit {
            request = request.query(&[("limit", limit)]);
        }

        let response = request.send().await?;
        self.handle_response(response).await
    }

    pub async fn get_project(&self, id_or_slug: &str) -> Result<Project, Error> {
        let url = format!("{}/project/{}", self.base_url, id_or_slug);
        let response = self.http.get(&url).send().await?;
        self.handle_response(response).await
    }

    pub async fn list_versions(&self, id_or_slug: &str) -> Result<Vec<Version>, Error> {
        let url = format!("{}/project/{}/version", self.base_url, id_or_slug);
        let response = self.http.get(&url).send().await?;
        self.handle_response(response).await
    }

    pub async fn get_categories(&self) -> Result<Vec<Category>, Error> {
        let url = format!("{}/tag/category", self.base_url);
        let response = self.http.get(&url).send().await?;
        self.handle_response(response).await
    }

    pub async fn get_loaders(&self) -> Result<Vec<Loader>, Error> {
        let url = format!("{}/tag/loader", self.base_url);
        let response = self.http.get(&url).send().await?;
        self.handle_response(response).await
    }

    pub async fn get_game_versions(&self) -> Result<Vec<GameVersion>, Error> {
        let url = format!("{}/tag/game_version", self.base_url);
        let response = self.http.get(&url).send().await?;
        self.handle_response(response).await
    }

    async fn handle_response<T: DeserializeOwned>(&self, response: reqwest::Response) -> Result<T, Error> {
        let status = response.status();
        if !status.is_success() {
            let text = response.text().await?;
            return Err(Error::Api(format!("{}: {}", status, text)));
        }
        let body = response.json::<T>().await?;
        Ok(body)
    }
}
'@

[System.IO.File]::WriteAllText("$srcDir\client.rs", $clientRs, $utf8NoBom)

Write-Host "Modrinth client crate created successfully."