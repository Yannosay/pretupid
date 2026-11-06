use serde_json::Value;
use std::collections::HashMap;

use crate::error::Error;
use crate::models::{
    Category, Dependency, DependencyType, DonationUrl, FileHashes, FileType, GameVersion,
    GameVersionType, License, Loader, Project, ProjectStatus, ProjectType, SearchResponse,
    SearchResult, Side, Version, VersionFile, VersionStatus, VersionType,
};

const API_BASE: &str = "https://api.modrinth.com/v2";

type ModrinthResult<T> = std::result::Result<T, Error>;

#[derive(Clone)]
pub struct ModrinthClient {
    http: reqwest::Client,
}

fn as_string(value: &Value) -> String {
    value.as_str().unwrap_or_default().to_string()
}

fn as_opt_string(value: &Value) -> Option<String> {
    value.as_str().map(String::from)
}

fn as_string_array(value: &Value) -> Vec<String> {
    value
        .as_array()
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str())
                .map(String::from)
                .collect()
        })
        .unwrap_or_default()
}

fn parse_project_type(value: &Value) -> ProjectType {
    match value.as_str().unwrap_or("") {
        "mod" => ProjectType::Mod,
        "modpack" => ProjectType::Modpack,
        "resourcepack" => ProjectType::ResourcePack,
        "shader" => ProjectType::Shader,
        _ => ProjectType::Unknown,
    }
}

fn parse_side(value: &Value) -> Side {
    match value.as_str().unwrap_or("") {
        "required" => Side::Required,
        "optional" => Side::Optional,
        "unsupported" => Side::Unsupported,
        "unknown" => Side::Unknown,
        _ => Side::Other,
    }
}

fn parse_status(value: &Value) -> ProjectStatus {
    match value.as_str().unwrap_or("") {
        "approved" => ProjectStatus::Approved,
        "rejected" => ProjectStatus::Rejected,
        "draft" => ProjectStatus::Draft,
        "unlisted" => ProjectStatus::Unlisted,
        "archived" => ProjectStatus::Archived,
        "processing" => ProjectStatus::Processing,
        "withheld" => ProjectStatus::Withheld,
        "scheduled" => ProjectStatus::Scheduled,
        "private" => ProjectStatus::Private,
        "unknown" => ProjectStatus::Unknown,
        _ => ProjectStatus::Other,
    }
}

fn parse_version_type(value: &Value) -> VersionType {
    match value.as_str().unwrap_or("") {
        "release" => VersionType::Release,
        "beta" => VersionType::Beta,
        "alpha" => VersionType::Alpha,
        _ => VersionType::Other,
    }
}

fn parse_version_status(value: &Value) -> VersionStatus {
    match value.as_str().unwrap_or("") {
        "listed" => VersionStatus::Listed,
        "archived" => VersionStatus::Archived,
        "draft" => VersionStatus::Draft,
        "unlisted" => VersionStatus::Unlisted,
        "scheduled" => VersionStatus::Scheduled,
        "unknown" => VersionStatus::Unknown,
        _ => VersionStatus::Other,
    }
}

fn parse_file_type(value: &Value) -> Option<FileType> {
    match value.as_str().unwrap_or("") {
        "required-resource-pack" => Some(FileType::RequiredResourcePack),
        "optional-resource-pack" => Some(FileType::OptionalResourcePack),
        _ => None,
    }
}

fn parse_dependency_type(value: &Value) -> DependencyType {
    match value.as_str().unwrap_or("") {
        "required" => DependencyType::Required,
        "optional" => DependencyType::Optional,
        "incompatible" => DependencyType::Incompatible,
        "embedded" => DependencyType::Embedded,
        _ => DependencyType::Other,
    }
}

fn parse_game_version_type(value: &Value) -> GameVersionType {
    match value.as_str().unwrap_or("") {
        "release" => GameVersionType::Release,
        "snapshot" => GameVersionType::Snapshot,
        "alpha" => GameVersionType::Alpha,
        "beta" => GameVersionType::Beta,
        "pre_release" => GameVersionType::PreRelease,
        _ => GameVersionType::Other,
    }
}

fn parse_string_or_object(value: &Value) -> String {
    if let Some(s) = value.as_str() {
        return s.to_string();
    }
    if let Some(map) = value.as_object() {
        if let Some(id) = map.get("id").and_then(Value::as_str) {
            return id.to_string();
        }
        if let Some(name) = map.get("name").and_then(Value::as_str) {
            return name.to_string();
        }
    }
    String::new()
}

fn parse_license(value: &Value) -> Option<License> {
    if value.is_null() {
        return None;
    }
    if let Some(id) = value.as_str() {
        return Some(License {
            id: id.to_string(),
            name: String::new(),
            url: None,
        });
    }
    if let Some(map) = value.as_object() {
        return Some(License {
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
        });
    }
    None
}

fn parse_donation_urls(value: &Value) -> Vec<DonationUrl> {
    let Some(arr) = value.as_array() else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|item| {
            if let Some(s) = item.as_str() {
                return Some(DonationUrl {
                    id: String::new(),
                    platform: s.to_string(),
                    url: String::new(),
                });
            }
            if let Some(map) = item.as_object() {
                return Some(DonationUrl {
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
                });
            }
            None
        })
        .collect()
}

fn parse_search_hit(hit: &Value) -> SearchResult {
    SearchResult {
        project_id: as_string(&hit["project_id"]),
        project_type: parse_project_type(&hit["project_type"]),
        slug: as_string(&hit["slug"]),
        author: as_string(&hit["author"]),
        title: as_string(&hit["title"]),
        description: as_string(&hit["description"]),
        categories: as_string_array(&hit["categories"]),
        display_categories: as_string_array(&hit["display_categories"]),
        versions: as_string_array(&hit["versions"]),
        downloads: hit["downloads"].as_u64().unwrap_or(0),
        follows: hit["follows"].as_u64().unwrap_or(0),
        icon_url: as_opt_string(&hit["icon_url"]),
        date_created: as_string(&hit["date_created"]),
        date_modified: as_string(&hit["date_modified"]),
        latest_version: as_opt_string(&hit["latest_version"]),
        license: parse_string_or_object(&hit["license"]),
        client_side: parse_side(&hit["client_side"]),
        server_side: parse_side(&hit["server_side"]),
        gallery: as_string_array(&hit["gallery"]),
        featured_gallery: as_opt_string(&hit["featured_gallery"]),
        color: hit["color"].as_u64().map(|v| v as u32),
    }
}

fn parse_project(value: &Value) -> Project {
    Project {
        id: as_string(&value["id"]),
        slug: as_opt_string(&value["slug"]),
        project_type: parse_project_type(&value["project_type"]),
        team: parse_string_or_object(&value["team"]),
        title: as_string(&value["title"]),
        description: as_string(&value["description"]),
        body: as_string(&value["body"]),
        published: as_string(&value["published"]),
        updated: as_string(&value["updated"]),
        approved: as_opt_string(&value["approved"]),
        status: parse_status(&value["status"]),
        moderator_message: as_opt_string(&value["moderator_message"]),
        license: parse_license(&value["license"]),
        downloads: value["downloads"].as_u64().unwrap_or(0),
        followers: value["followers"].as_u64().unwrap_or(0),
        categories: as_string_array(&value["categories"]),
        additional_categories: as_string_array(&value["additional_categories"]),
        versions: as_string_array(&value["versions"]),
        icon_url: as_opt_string(&value["icon_url"]),
        issues_url: as_opt_string(&value["issues_url"]),
        source_url: as_opt_string(&value["source_url"]),
        wiki_url: as_opt_string(&value["wiki_url"]),
        discord_url: as_opt_string(&value["discord_url"]),
        donation_urls: parse_donation_urls(&value["donation_urls"]),
        gallery: as_string_array(&value["gallery"]),
        featured_gallery: as_opt_string(&value["featured_gallery"]),
        color: value["color"].as_u64().map(|v| v as u32),
        client_side: parse_side(&value["client_side"]),
        server_side: parse_side(&value["server_side"]),
    }
}

fn parse_version(value: &Value) -> Version {
    Version {
        id: as_string(&value["id"]),
        project_id: as_string(&value["project_id"]),
        author_id: as_string(&value["author_id"]),
        featured: value["featured"].as_bool().unwrap_or(false),
        name: as_string(&value["name"]),
        version_number: as_string(&value["version_number"]),
        changelog: as_opt_string(&value["changelog"]),
        changelog_url: as_opt_string(&value["changelog_url"]),
        date_published: as_string(&value["date_published"]),
        downloads: value["downloads"].as_u64().unwrap_or(0),
        version_type: parse_version_type(&value["version_type"]),
        status: parse_version_status(&value["status"]),
        requested_status: value.get("requested_status").map(parse_version_status),
        files: value["files"]
            .as_array()
            .map(|arr| {
                arr.iter()
                    .map(|f| VersionFile {
                        hashes: FileHashes {
                            sha1: as_string(&f["hashes"]["sha1"]),
                            sha512: as_string(&f["hashes"]["sha512"]),
                        },
                        url: as_string(&f["url"]),
                        filename: as_string(&f["filename"]),
                        primary: f["primary"].as_bool().unwrap_or(false),
                        size: f["size"].as_u64().unwrap_or(0),
                        file_type: parse_file_type(&f["file_type"]),
                    })
                    .collect()
            })
            .unwrap_or_default(),
        dependencies: value["dependencies"]
            .as_array()
            .map(|arr| {
                arr.iter()
                    .map(|d| Dependency {
                        version_id: as_opt_string(&d["version_id"]),
                        project_id: as_opt_string(&d["project_id"]),
                        file_name: as_opt_string(&d["file_name"]),
                        dependency_type: parse_dependency_type(&d["dependency_type"]),
                    })
                    .collect()
            })
            .unwrap_or_default(),
        game_versions: as_string_array(&value["game_versions"]),
        loaders: as_string_array(&value["loaders"]),
    }
}

impl ModrinthClient {
    pub fn new() -> ModrinthResult<Self> {
        let http = reqwest::Client::builder()
            .user_agent(concat!(
                "pretupid-launcher/",
                env!("CARGO_PKG_VERSION"),
                " (contact: github.com/yannosay)"
            ))
            .timeout(std::time::Duration::from_secs(60))
            .build()
            .map_err(Error::Http)?;

        Ok(Self { http })
    }

    async fn get_json(&self, url: &str) -> ModrinthResult<Value> {
        let response = self.http.get(url).send().await.map_err(Error::Http)?;
        if !response.status().is_success() {
            return Err(Error::Api(format!(
                "API returned status {}",
                response.status()
            )));
        }
        let body = response.text().await.map_err(Error::Http)?;
        serde_json::from_str(&body).map_err(Error::Json)
    }

    pub async fn search_projects(
        &self,
        query: &str,
        facets: Option<&str>,
        index: Option<&str>,
        offset: Option<u32>,
        limit: Option<u32>,
    ) -> ModrinthResult<SearchResponse> {
        let mut url = format!("{}/search?query={}", API_BASE, urlencoding::encode(query));
        if let Some(f) = facets {
            url.push_str(&format!("&facets={}", urlencoding::encode(f)));
        }
        if let Some(i) = index {
            url.push_str(&format!("&index={}", urlencoding::encode(i)));
        }
        if let Some(o) = offset {
            url.push_str(&format!("&offset={o}"));
        }
        if let Some(l) = limit {
            url.push_str(&format!("&limit={l}"));
        }
        let json = self.get_json(&url).await?;
        let hits = json["hits"]
            .as_array()
            .ok_or_else(|| Error::Api("missing hits array".to_string()))?;
        Ok(SearchResponse {
            hits: hits.iter().map(parse_search_hit).collect(),
            offset: json["offset"].as_u64().unwrap_or(0) as u32,
            limit: json["limit"].as_u64().unwrap_or(0) as u32,
            total_hits: json["total_hits"].as_u64().unwrap_or(0) as u32,
        })
    }

    pub async fn get_project(&self, id_or_slug: &str) -> ModrinthResult<Project> {
        let url = format!("{}/project/{}", API_BASE, urlencoding::encode(id_or_slug));
        let json = self.get_json(&url).await?;
        Ok(parse_project(&json))
    }

    pub async fn list_versions(&self, id_or_slug: &str) -> ModrinthResult<Vec<Version>> {
        let url = format!(
            "{}/project/{}/version",
            API_BASE,
            urlencoding::encode(id_or_slug)
        );
        let json = self.get_json(&url).await?;
        let arr = json
            .as_array()
            .ok_or_else(|| Error::Api("expected array of versions".to_string()))?;
        Ok(arr.iter().map(parse_version).collect())
    }

    pub async fn get_categories(&self) -> ModrinthResult<Vec<Category>> {
        let url = format!("{}/tag/category", API_BASE);
        let json = self.get_json(&url).await?;
        let arr = json
            .as_array()
            .ok_or_else(|| Error::Api("expected array".to_string()))?;
        Ok(arr
            .iter()
            .map(|v| Category {
                name: as_string(&v["name"]),
                project_type: parse_project_type(&v["project_type"]),
                icon: as_string(&v["icon"]),
                header: as_string(&v["header"]),
            })
            .collect())
    }

    pub async fn get_loaders(&self) -> ModrinthResult<Vec<Loader>> {
        let url = format!("{}/tag/loader", API_BASE);
        let json = self.get_json(&url).await?;
        let arr = json
            .as_array()
            .ok_or_else(|| Error::Api("expected array".to_string()))?;
        Ok(arr
            .iter()
            .map(|v| Loader {
                name: as_string(&v["name"]),
                icon: as_string(&v["icon"]),
                supported_project_types: v["supported_project_types"]
                    .as_array()
                    .map(|a| a.iter().map(parse_project_type).collect())
                    .unwrap_or_default(),
            })
            .collect())
    }

    pub async fn get_game_versions(&self) -> ModrinthResult<Vec<GameVersion>> {
        let url = format!("{}/tag/game_version", API_BASE);
        let json = self.get_json(&url).await?;
        let arr = json
            .as_array()
            .ok_or_else(|| Error::Api("expected array".to_string()))?;
        Ok(arr
            .iter()
            .map(|v| GameVersion {
                version: as_string(&v["version"]),
                version_type: parse_game_version_type(&v["version_type"]),
                date: as_string(&v["date"]),
                major: v["major"].as_bool().unwrap_or(false),
            })
            .collect())
    }

    pub async fn get_versions_from_hashes(
        &self,
        hashes: &[String],
    ) -> ModrinthResult<HashMap<String, Version>> {
        if hashes.is_empty() {
            return Ok(HashMap::new());
        }

        let url = format!("{}/version_files", API_BASE);
        let body = serde_json::json!({
            "hashes": hashes,
            "algorithm": "sha1"
        });

        let response = self
            .http
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(Error::Http)?;

        if !response.status().is_success() {
            return Err(Error::Api(format!(
                "version_files returned status {}",
                response.status()
            )));
        }

        let body: Value = response.json().await.map_err(Error::Http)?;
        let map = body
            .as_object()
            .ok_or_else(|| Error::Api("expected object from version_files".to_string()))?;

        let mut result = HashMap::new();
        for (hash, value) in map {
            result.insert(hash.clone(), parse_version(value));
        }
        Ok(result)
    }

    pub async fn get_version(&self, version_id: &str) -> ModrinthResult<Version> {
        let url = format!("{}/version/{}", API_BASE, urlencoding::encode(version_id));
        let json = self.get_json(&url).await?;
        Ok(parse_version(&json))
    }

    pub async fn list_versions_filtered(
        &self,
        id_or_slug: &str,
        loaders: &[String],
        game_versions: &[String],
    ) -> ModrinthResult<Vec<Version>> {
        let mut url = format!(
            "{}/project/{}/version?include_changelog=false",
            API_BASE,
            urlencoding::encode(id_or_slug)
        );
        if !loaders.is_empty() {
            let json = serde_json::to_string(loaders).map_err(Error::Json)?;
            url.push_str(&format!("&loaders={}", urlencoding::encode(&json)));
        }
        if !game_versions.is_empty() {
            let json = serde_json::to_string(game_versions).map_err(Error::Json)?;
            url.push_str(&format!("&game_versions={}", urlencoding::encode(&json)));
        }
        let json = self.get_json(&url).await?;
        let arr = json
            .as_array()
            .ok_or_else(|| Error::Api("expected array of versions".to_string()))?;
        Ok(arr.iter().map(parse_version).collect())
    }

    pub async fn get_latest_versions_from_hashes(
        &self,
        hashes: &[String],
        loaders: &[String],
        game_versions: &[String],
    ) -> ModrinthResult<HashMap<String, Version>> {
        if hashes.is_empty() {
            return Ok(HashMap::new());
        }
        let url = format!("{}/version_files/update", API_BASE);
        let mut body = serde_json::json!({
            "hashes": hashes,
            "algorithm": "sha1",
        });
        if !loaders.is_empty() {
            body["loaders"] = serde_json::json!(loaders);
        }
        if !game_versions.is_empty() {
            body["game_versions"] = serde_json::json!(game_versions);
        }
        let response = self
            .http
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(Error::Http)?;
        if !response.status().is_success() {
            return Err(Error::Api(format!(
                "version_files/update returned status {}",
                response.status()
            )));
        }
        let body: Value = response.json().await.map_err(Error::Http)?;
        let map = body
            .as_object()
            .ok_or_else(|| Error::Api("expected object from version_files/update".to_string()))?;
        Ok(map
            .iter()
            .map(|(hash, value)| (hash.clone(), parse_version(value)))
            .collect())
    }

    pub async fn get_projects_by_ids(&self, ids: &[String]) -> ModrinthResult<Vec<Project>> {
        if ids.is_empty() {
            return Ok(Vec::new());
        }

        let ids_json = serde_json::to_string(ids).map_err(Error::Json)?;
        let url = format!(
            "{}/projects?ids={}",
            API_BASE,
            urlencoding::encode(&ids_json)
        );
        let json = self.get_json(&url).await?;
        let arr = json
            .as_array()
            .ok_or_else(|| Error::Api("expected array of projects".to_string()))?;
        Ok(arr.iter().map(parse_project).collect())
    }
}
