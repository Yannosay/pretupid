use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use futures_util::stream::{self, StreamExt};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt};
use tokio::process::Command;
use tokio::sync::mpsc;
use tokio::sync::watch;

use crate::error::{LaunchError, LaunchResult};
use crate::models::{FabricProfile, LaunchSettings, Library, LibraryDownloads, VersionDetails};
use crate::version::VersionResolver;

#[derive(Debug, Clone)]
pub enum LaunchEvent {
    Progress { current: u64, total: u64, message: String },
    Log { line: String },
    Started { pid: u32 },
    Exited { code: Option<i32> },
    Failed { message: String },
}

struct DownloadFile {
    url: String,
    path: PathBuf,
    size: u64,
}

pub struct Launcher {
    resolver: VersionResolver,
    profiles_dir: PathBuf,
    caches_dir: PathBuf,
}

impl Launcher {
    pub fn new(profiles_dir: PathBuf, caches_dir: PathBuf, resolver: VersionResolver) -> Self {
        Self {
            resolver,
            profiles_dir,
            caches_dir,
        }
    }

    fn classpath_separator() -> char {
        if cfg!(target_os = "windows") { ';' } else { ':' }
    }

    pub async fn launch(
        &self,
        profile_name: &str,
        version_id: &str,
        settings: &LaunchSettings,
        auth_token: &str,
        username: &str,
        uuid: &str,
        mod_loader: &str,
        loader_version: Option<&str>,
        event_tx: mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        let result = self
            .launch_inner(
                profile_name,
                version_id,
                settings,
                auth_token,
                username,
                uuid,
                mod_loader,
                loader_version,
                event_tx.clone(),
                cancel_rx,
            )
            .await;

        match result {
            Ok(()) => Ok(()),
            Err(e) => {
                event_tx
                    .send(LaunchEvent::Log {
                        line: format!("Launch failed: {}", e),
                    })
                    .ok();
                event_tx
                    .send(LaunchEvent::Failed {
                        message: e.to_string(),
                    })
                    .ok();
                event_tx.send(LaunchEvent::Exited { code: Some(1) }).ok();
                Err(e)
            }
        }
    }

    async fn launch_inner(
        &self,
        profile_name: &str,
        version_id: &str,
        settings: &LaunchSettings,
        auth_token: &str,
        username: &str,
        uuid: &str,
        mod_loader: &str,
        loader_version: Option<&str>,
        event_tx: mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        let profile_dir = self.profiles_dir.join(profile_name);
        std::fs::create_dir_all(&profile_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        if mod_loader != "vanilla" {
            std::fs::create_dir_all(profile_dir.join("mods"))
                .map_err(|e| LaunchError::Io(e.to_string()))?;
        }

        let libraries_dir = self.caches_dir.join("libraries");
        let assets_dir = self.caches_dir.join("assets");
        let versions_cache_dir = self.caches_dir.join("versions");
        std::fs::create_dir_all(&libraries_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        std::fs::create_dir_all(&assets_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        std::fs::create_dir_all(&versions_cache_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let mut version_details = self.resolver.get_version_details(version_id).await?;

        match mod_loader {
            "vanilla" => {}
            "modrinth" | "fabric" => {
                let loader_ver = loader_version.unwrap_or("latest");
                let profile = self.fetch_fabric_profile(&version_details.id, loader_ver).await?;
                version_details.libraries.extend(
                    profile
                        .libraries
                        .into_iter()
                        .map(Self::fabric_library_to_library),
                );
                version_details.main_class = profile.main_class;
            }
            "quilt" => {
                let loader_ver = loader_version.unwrap_or("latest");
                let profile = self.fetch_quilt_profile(&version_details.id, loader_ver).await?;
                version_details.libraries.extend(
                    profile
                        .libraries
                        .into_iter()
                        .map(Self::fabric_library_to_library),
                );
                version_details.main_class = profile.main_class;
            }
            "forge" => {
                let forge_version = loader_version.ok_or_else(|| {
                    LaunchError::LaunchFailed("Forge loader version is required".to_string())
                })?;
                let forge_json = self
                    .install_forge(
                        &version_details.id,
                        forge_version,
                        &libraries_dir,
                        &event_tx,
                        &cancel_rx,
                    )
                    .await?;
                version_details.libraries.extend(forge_json.libraries);
                version_details.main_class = forge_json.main_class;
                version_details.arguments = forge_json.arguments;
                version_details.minecraft_arguments = forge_json.minecraft_arguments;
            }
            "neoforge" => {
                let neoforge_version = loader_version.ok_or_else(|| {
                    LaunchError::LaunchFailed("NeoForge loader version is required".to_string())
                })?;
                let neoforge_json = self
                    .install_neoforge(
                        &version_details.id,
                        neoforge_version,
                        &libraries_dir,
                        &event_tx,
                        &cancel_rx,
                    )
                    .await?;
                version_details.libraries.extend(neoforge_json.libraries);
                version_details.main_class = neoforge_json.main_class;
                version_details.arguments = neoforge_json.arguments;
                version_details.minecraft_arguments = neoforge_json.minecraft_arguments;
            }
            _ => {
                return Err(LaunchError::LaunchFailed(format!(
                    "Unsupported loader: {}",
                    mod_loader
                )))
            }
        }

        let mut files = Vec::new();

        let client = &version_details.downloads.client;
        let version_dir = versions_cache_dir.join(&version_details.id);
        std::fs::create_dir_all(&version_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        let client_path = version_dir.join(format!("{}.jar", version_details.id));
        if !client_path.exists() {
            files.push(DownloadFile {
                url: client.url.clone(),
                path: client_path,
                size: client.size,
            });
        }

        for library in &version_details.libraries {
            if let Some(artifact) = &library.downloads.artifact {
                let path = libraries_dir.join(&artifact.path);
                if !path.exists() {
                    files.push(DownloadFile {
                        url: artifact.url.clone(),
                        path,
                        size: artifact.size,
                    });
                }
            }
        }

        let indexes_dir = assets_dir.join("indexes");
        std::fs::create_dir_all(&indexes_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        let index_path = indexes_dir.join(format!("{}.json", version_details.asset_index.id));
        if !index_path.exists() {
            files.push(DownloadFile {
                url: version_details.asset_index.url.clone(),
                path: index_path.clone(),
                size: version_details.asset_index.size,
            });
        }

        self.download_files(files, &event_tx, &cancel_rx).await?;

        let index_content = tokio::fs::read_to_string(&index_path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        let asset_index: crate::models::AssetIndexDetails = serde_json::from_str(&index_content)
            .map_err(|e| LaunchError::Parse(e.to_string()))?;

        let mut asset_files = Vec::new();
        let objects_dir = assets_dir.join("objects");
        std::fs::create_dir_all(&objects_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        for (_name, object) in &asset_index.objects {
            let sub_dir = &object.hash[0..2];
            let asset_path = objects_dir.join(sub_dir).join(&object.hash);
            if !asset_path.exists() {
                asset_files.push(DownloadFile {
                    url: format!(
                        "https://resources.download.minecraft.net/{}/{}",
                        sub_dir, object.hash
                    ),
                    path: asset_path,
                    size: object.size,
                });
            }
        }

        self.download_files(asset_files, &event_tx, &cancel_rx).await?;

        self.prepare_legacy_assets(&version_details, &assets_dir).await?;
        self.extract_natives(&version_details, &libraries_dir).await?;

        let java_major = version_details
            .java_version
            .as_ref()
            .map(|j| j.major_version)
            .unwrap_or(8);
        let supports_native_access = java_major >= 17;

        let assets_for_launch = if version_details.assets == "legacy"
            || version_details.assets == "pre-1.6"
        {
            assets_dir.join("virtual").join("legacy")
        } else {
            assets_dir.clone()
        };

        let args = self.build_arguments(
            &version_details,
            &profile_dir,
            &libraries_dir,
            &assets_for_launch,
            &versions_cache_dir,
            settings,
            auth_token,
            username,
            uuid,
            supports_native_access,
        );

        let java_path = match &settings.java_path {
            Some(p) => PathBuf::from(p),
            None => {
                crate::java::JavaRuntime::detect_for_version(
                    java_major,
                    settings.auto_install_java,
                    self.caches_dir.join("java"),
                    event_tx.clone(),
                    cancel_rx.clone(),
                )
                .await?
            }
        };

        let mut command = Command::new(&java_path);
        if java_major >= 9 {
            let argfile_path = self.write_argfile(&profile_dir, &args).await?;
            command.arg(format!("@{}", argfile_path.display()));
        } else {
            command.args(&args);
        }
        command
            .current_dir(&profile_dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .stdin(Stdio::null());

        let mut child = command
            .spawn()
            .map_err(|e| LaunchError::LaunchFailed(e.to_string()))?;

        if let Some(pid) = child.id() {
            event_tx.send(LaunchEvent::Started { pid }).ok();
            let mut kill_cancel = cancel_rx.clone();
            tokio::spawn(async move {
                loop {
                    if kill_cancel.changed().await.is_err() {
                        break;
                    }
                    if *kill_cancel.borrow() {
                        Self::terminate_process(pid).await;
                        break;
                    }
                }
            });
        }

        let stdout = child.stdout.take();
        let stderr = child.stderr.take();

        let tx_stdout = event_tx.clone();
        if let Some(stdout) = stdout {
            tokio::spawn(async move {
                let mut reader = tokio::io::BufReader::new(stdout).lines();
                while let Ok(Some(line)) = reader.next_line().await {
                    tx_stdout.send(LaunchEvent::Log { line }).ok();
                }
            });
        }

        let tx_stderr = event_tx.clone();
        if let Some(stderr) = stderr {
            tokio::spawn(async move {
                let mut reader = tokio::io::BufReader::new(stderr).lines();
                while let Ok(Some(line)) = reader.next_line().await {
                    tx_stderr.send(LaunchEvent::Log { line }).ok();
                }
            });
        }

        let status = child
            .wait()
            .await
            .map_err(|e| LaunchError::LaunchFailed(e.to_string()))?;

        event_tx
            .send(LaunchEvent::Exited {
                code: status.code(),
            })
            .ok();

        Ok(())
    }

    fn fabric_library_to_library(lib: crate::models::FabricLibrary) -> Library {
        let artifact = match lib.downloads.and_then(|d| d.artifact) {
            Some(a) => a,
            None => {
                let maven_path = Self::maven_path_from_name(&lib.name);
                let base_url = lib
                    .url
                    .clone()
                    .unwrap_or_else(|| "https://maven.fabricmc.net/".to_string());
                let clean_base = if base_url.ends_with('/') {
                    base_url
                } else {
                    format!("{}/", base_url)
                };
                crate::models::Artifact {
                    path: maven_path.clone(),
                    sha1: lib.sha1.clone().unwrap_or_default(),
                    size: lib.size.unwrap_or(0),
                    url: format!("{}{}", clean_base, maven_path),
                }
            }
        };

        Library {
            name: lib.name,
            downloads: LibraryDownloads {
                artifact: Some(artifact),
                classifiers: None,
            },
            natives: None,
            rules: None,
        }
    }

    async fn fetch_fabric_profile(
        &self,
        game_version: &str,
        loader_version: &str,
    ) -> LaunchResult<FabricProfile> {
        let loader_ver = if loader_version == "latest" || loader_version.is_empty() {
            self.resolve_fabric_loader_version().await?
        } else {
            loader_version.to_string()
        };

        let url = format!(
            "https://meta.fabricmc.net/v2/versions/loader/{}/{}/profile/json",
            game_version, loader_ver
        );
        let response = reqwest::get(&url)
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "Fabric profile fetch failed: {}",
                response.status()
            )));
        }
        let profile: FabricProfile = response
            .json()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;
        Ok(profile)
    }

    async fn resolve_fabric_loader_version(&self) -> LaunchResult<String> {
        let url = "https://meta.fabricmc.net/v2/versions/loader";
        let response = reqwest::get(url)
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "Fabric loader list fetch failed: {}",
                response.status()
            )));
        }
        let versions: Vec<serde_json::Value> = response
            .json()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;
        let stable = versions
            .iter()
            .filter(|v| v["stable"].as_bool().unwrap_or(false))
            .filter_map(|v| v["version"].as_str().map(|s| s.to_string()))
            .next();
        if let Some(v) = stable {
            Ok(v)
        } else {
            versions
                .first()
                .and_then(|v| v["version"].as_str().map(|s| s.to_string()))
                .ok_or_else(|| LaunchError::Network("No Fabric loader versions found".to_string()))
        }
    }

    async fn fetch_quilt_profile(
        &self,
        game_version: &str,
        loader_version: &str,
    ) -> LaunchResult<FabricProfile> {
        let loader_ver = if loader_version == "latest" || loader_version.is_empty() {
            self.resolve_quilt_loader_version().await?
        } else {
            loader_version.to_string()
        };

        let url = format!(
            "https://meta.quiltmc.org/v3/versions/loader/{}/{}/profile/json",
            game_version, loader_ver
        );
        let response = reqwest::get(&url)
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "Quilt profile fetch failed: {}",
                response.status()
            )));
        }
        let profile: FabricProfile = response
            .json()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;
        Ok(profile)
    }

    async fn resolve_quilt_loader_version(&self) -> LaunchResult<String> {
        let url = "https://meta.quiltmc.org/v3/versions/loader";
        let response = reqwest::get(url)
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "Quilt loader list fetch failed: {}",
                response.status()
            )));
        }
        let versions: Vec<serde_json::Value> = response
            .json()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;
        let stable = versions
            .iter()
            .filter(|v| v["stable"].as_bool().unwrap_or(false))
            .filter_map(|v| v["version"].as_str().map(|s| s.to_string()))
            .next();
        if let Some(v) = stable {
            Ok(v)
        } else {
            versions
                .first()
                .and_then(|v| v["version"].as_str().map(|s| s.to_string()))
                .ok_or_else(|| LaunchError::Network("No Quilt loader versions found".to_string()))
        }
    }

    async fn install_forge(
        &self,
        mc_version: &str,
        forge_version: &str,
        libraries_dir: &PathBuf,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<VersionDetails> {
        let full_version = format!("{}-{}", mc_version, forge_version);
        let installer_url = format!(
            "https://maven.minecraftforge.net/net/minecraftforge/forge/{}/forge-{}-installer.jar",
            full_version, full_version
        );
        let installer_path = libraries_dir.join(format!("forge-{}-installer.jar", full_version));
        self.download_file_simple(&installer_url, &installer_path).await?;

        let install_dir = self.caches_dir.join("forge_install");
        std::fs::create_dir_all(&install_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let java_path = crate::java::JavaRuntime::detect_for_version(
            8,
            true,
            self.caches_dir.join("java"),
            event_tx.clone(),
            cancel_rx.clone(),
        )
        .await?;

        let status = Command::new(&java_path)
            .arg("-jar")
            .arg(&installer_path)
            .arg("--installClient")
            .arg(&install_dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .stdin(Stdio::null())
            .status()
            .await
            .map_err(|e| LaunchError::LaunchFailed(e.to_string()))?;

        if !status.success() {
            return Err(LaunchError::LaunchFailed(format!(
                "Forge installer failed with exit code: {:?}",
                status.code()
            )));
        }

        let version_json_path = install_dir
            .join("versions")
            .join(format!("{}.json", full_version));
        if !version_json_path.exists() {
            return Err(LaunchError::LaunchFailed(
                "Forge installer did not produce a version JSON".to_string(),
            ));
        }

        let content = tokio::fs::read_to_string(&version_json_path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        let details: VersionDetails =
            serde_json::from_str(&content).map_err(|e| LaunchError::Parse(e.to_string()))?;

        for lib in &details.libraries {
            if let Some(artifact) = &lib.downloads.artifact {
                let source = install_dir.join("libraries").join(&artifact.path);
                let dest = libraries_dir.join(&artifact.path);
                if source.exists() && !dest.exists() {
                    if let Some(parent) = dest.parent() {
                        std::fs::create_dir_all(parent)
                            .map_err(|e| LaunchError::Io(e.to_string()))?;
                    }
                    tokio::fs::copy(&source, &dest)
                        .await
                        .map_err(|e| LaunchError::Io(e.to_string()))?;
                }
            }
        }

        Ok(details)
    }

    async fn install_neoforge(
        &self,
        mc_version: &str,
        neoforge_version: &str,
        libraries_dir: &PathBuf,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<VersionDetails> {
        let full_version = neoforge_version;
        let installer_url = format!(
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/{}/neoforge-{}-installer.jar",
            full_version, full_version
        );
        let installer_path = libraries_dir.join(format!("neoforge-{}-installer.jar", full_version));
        self.download_file_simple(&installer_url, &installer_path).await?;

        let install_dir = self.caches_dir.join("neoforge_install");
        std::fs::create_dir_all(&install_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let java_path = crate::java::JavaRuntime::detect_for_version(
            17,
            true,
            self.caches_dir.join("java"),
            event_tx.clone(),
            cancel_rx.clone(),
        )
        .await?;

        let status = Command::new(&java_path)
            .arg("-jar")
            .arg(&installer_path)
            .arg("--installClient")
            .arg(&install_dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .stdin(Stdio::null())
            .status()
            .await
            .map_err(|e| LaunchError::LaunchFailed(e.to_string()))?;

        if !status.success() {
            return Err(LaunchError::LaunchFailed(format!(
                "NeoForge installer failed with exit code: {:?}",
                status.code()
            )));
        }

        let version_json_path = install_dir
            .join("versions")
            .join(format!("{}-{}.json", mc_version, full_version));
        if !version_json_path.exists() {
            return Err(LaunchError::LaunchFailed(
                "NeoForge installer did not produce a version JSON".to_string(),
            ));
        }

        let content = tokio::fs::read_to_string(&version_json_path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        let details: VersionDetails =
            serde_json::from_str(&content).map_err(|e| LaunchError::Parse(e.to_string()))?;

        for lib in &details.libraries {
            if let Some(artifact) = &lib.downloads.artifact {
                let source = install_dir.join("libraries").join(&artifact.path);
                let dest = libraries_dir.join(&artifact.path);
                if source.exists() && !dest.exists() {
                    if let Some(parent) = dest.parent() {
                        std::fs::create_dir_all(parent)
                            .map_err(|e| LaunchError::Io(e.to_string()))?;
                    }
                    tokio::fs::copy(&source, &dest)
                        .await
                        .map_err(|e| LaunchError::Io(e.to_string()))?;
                }
            }
        }

        Ok(details)
    }

    async fn download_file_simple(&self, url: &str, path: &PathBuf) -> LaunchResult<()> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| LaunchError::Io(e.to_string()))?;
        }
        let response = reqwest::get(url)
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "Download failed: {}",
                response.status()
            )));
        }
        let bytes = response
            .bytes()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;
        let mut file = tokio::fs::File::create(path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        file.write_all(&bytes)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        Ok(())
    }

    async fn download_files(
        &self,
        files: Vec<DownloadFile>,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        if files.is_empty() {
            return Ok(());
        }

        let total_size: u64 = files.iter().map(|f| f.size).sum();
        let downloaded = Arc::new(AtomicU64::new(0));

        let reporter_tx = event_tx.clone();
        let reporter_downloaded = downloaded.clone();
        let reporter_cancel = cancel_rx.clone();
        tokio::spawn(async move {
            Self::report_progress(reporter_downloaded, total_size, reporter_tx, reporter_cancel)
                .await;
        });

        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(300))
            .build()
            .map_err(|e| LaunchError::Network(e.to_string()))?;

        let download_tasks = stream::iter(files.into_iter().map(|file| {
            let downloaded = downloaded.clone();
            let cancel_rx = cancel_rx.clone();
            let client = client.clone();
            async move {
                Self::download_file(&client, file.url, file.path, downloaded, cancel_rx).await
            }
        }))
        .buffer_unordered(8)
        .collect::<Vec<Result<(), LaunchError>>>()
        .await;

        for result in download_tasks {
            result?;
        }

        if *cancel_rx.borrow() {
            return Err(LaunchError::Cancelled);
        }

        event_tx
            .send(LaunchEvent::Progress {
                current: total_size,
                total: total_size,
                message: "Download complete".to_string(),
            })
            .ok();

        Ok(())
    }

    async fn report_progress(
        downloaded: Arc<AtomicU64>,
        total_size: u64,
        event_tx: mpsc::UnboundedSender<LaunchEvent>,
        mut cancel_rx: watch::Receiver<bool>,
    ) {
        let mut interval = tokio::time::interval(std::time::Duration::from_millis(200));
        loop {
            tokio::select! {
                _ = cancel_rx.changed() => {
                    if *cancel_rx.borrow() {
                        return;
                    }
                }
                _ = interval.tick() => {
                    let current = downloaded.load(Ordering::SeqCst);
                    if total_size > 0 && current >= total_size {
                        return;
                    }
                    let message = if total_size > 0 {
                        format!(
                            "Downloading Minecraft ({} / {} MB)",
                            current / 1024 / 1024,
                            total_size / 1024 / 1024
                        )
                    } else {
                        format!("Downloading ({} MB)", current / 1024 / 1024)
                    };
                    event_tx
                        .send(LaunchEvent::Progress {
                            current,
                            total: total_size,
                            message,
                        })
                        .ok();
                }
            }
        }
    }

    async fn download_file(
        client: &reqwest::Client,
        url: String,
        path: PathBuf,
        downloaded: Arc<AtomicU64>,
        mut cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| LaunchError::Io(e.to_string()))?;
        }

        let response = client
            .get(&url)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;

        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "download failed with status {} for {}",
                response.status(),
                url
            )));
        }

        let mut file = tokio::fs::File::create(&path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        let mut stream = response.bytes_stream();
        loop {
            tokio::select! {
                _ = cancel_rx.changed() => {
                    if *cancel_rx.borrow() {
                        let _ = tokio::fs::remove_file(&path).await;
                        return Err(LaunchError::Cancelled);
                    }
                }
                chunk = stream.next() => {
                    match chunk {
                        Some(Ok(bytes)) => {
                            downloaded.fetch_add(bytes.len() as u64, Ordering::SeqCst);
                            file.write_all(&bytes)
                                .await
                                .map_err(|e| LaunchError::Io(e.to_string()))?;
                        }
                        Some(Err(e)) => {
                            return Err(LaunchError::Network(e.to_string()));
                        }
                        None => break,
                    }
                }
            }
        }

        Ok(())
    }

    #[cfg(windows)]
    async fn terminate_process(pid: u32) {
        let _ = tokio::process::Command::new("taskkill")
            .args(["/F", "/PID", &pid.to_string()])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .await;
    }

    #[cfg(not(windows))]
    async fn terminate_process(pid: u32) {
        let _ = tokio::process::Command::new("kill")
            .args(["-9", &pid.to_string()])
            .status()
            .await;
    }

    async fn prepare_legacy_assets(
        &self,
        version: &VersionDetails,
        assets_dir: &PathBuf,
    ) -> LaunchResult<()> {
        let assets_name = version.assets.as_str();
        let is_legacy = assets_name == "legacy" || assets_name == "pre-1.6";
        if !is_legacy {
            return Ok(());
        }

        let index_path = assets_dir
            .join("indexes")
            .join(format!("{}.json", version.asset_index.id));
        let index_content = tokio::fs::read_to_string(&index_path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        let asset_index: crate::models::AssetIndexDetails = serde_json::from_str(&index_content)
            .map_err(|e| LaunchError::Parse(e.to_string()))?;

        let legacy_dir = assets_dir.join("virtual").join("legacy");
        tokio::fs::create_dir_all(&legacy_dir)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        for (name, object) in &asset_index.objects {
            let source_path = assets_dir
                .join("objects")
                .join(&object.hash[0..2])
                .join(&object.hash);

            if !source_path.exists() {
                continue;
            }

            let target_path = legacy_dir.join(name);
            if let Some(parent) = target_path.parent() {
                tokio::fs::create_dir_all(parent)
                    .await
                    .map_err(|e| LaunchError::Io(e.to_string()))?;
            }

            tokio::fs::copy(&source_path, &target_path)
                .await
                .map_err(|e| LaunchError::Io(e.to_string()))?;
        }

        Ok(())
    }

    async fn extract_natives(
        &self,
        version: &VersionDetails,
        libraries_dir: &PathBuf,
    ) -> LaunchResult<()> {
        let parent = libraries_dir
            .parent()
            .ok_or_else(|| LaunchError::Io("invalid libraries directory".to_string()))?;
        let natives_dir = parent.join("natives");
        std::fs::create_dir_all(&natives_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let os_key = Self::current_os_key();
        let arch_key = "64";

        for library in &version.libraries {
            let mut native_sources: Vec<&crate::models::Artifact> = Vec::new();

            if let Some(natives_map) = &library.natives {
                if let Some(classifier_template) = natives_map.get(os_key) {
                    let classifier = classifier_template.replace("${arch}", arch_key);
                    if let Some(classifier_artifact) = library
                        .downloads
                        .classifiers
                        .as_ref()
                        .and_then(|c| c.get(&classifier))
                    {
                        native_sources.push(classifier_artifact);
                    }
                }
            }

            for native_artifact in native_sources {
                let jar_path = libraries_dir.join(&native_artifact.path);
                if !jar_path.exists() {
                    self.download_file_simple(&native_artifact.url, &jar_path)
                        .await?;
                }

                Self::extract_jar_natives(&jar_path, &natives_dir)?;
            }
        }

        Ok(())
    }

    fn current_os_key() -> &'static str {
        if cfg!(target_os = "windows") {
            "windows"
        } else if cfg!(target_os = "macos") {
            "osx"
        } else {
            "linux"
        }
    }

    fn extract_jar_natives(jar_path: &PathBuf, natives_dir: &PathBuf) -> LaunchResult<()> {
        let file = std::fs::File::open(jar_path).map_err(|e| LaunchError::Io(e.to_string()))?;

        let mut archive = zip::ZipArchive::new(file)
            .map_err(|e| LaunchError::Parse(format!("invalid jar archive: {}", e)))?;

        for i in 0..archive.len() {
            let mut entry = archive
                .by_index(i)
                .map_err(|e| LaunchError::Parse(e.to_string()))?;

            let name = match entry.enclosed_name() {
                Some(n) => n.to_path_buf(),
                None => continue,
            };

            let file_name = match name.file_name().and_then(|n| n.to_str()) {
                Some(f) => f.to_string(),
                None => continue,
            };

            let ext = Path::new(&file_name)
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_lowercase();

            if ext != "dll" && ext != "so" && ext != "dylib" && ext != "jnilib" {
                continue;
            }

            let out_path = natives_dir.join(&file_name);
            let mut out_file =
                std::fs::File::create(&out_path).map_err(|e| LaunchError::Io(e.to_string()))?;

            std::io::copy(&mut entry, &mut out_file)
                .map_err(|e| LaunchError::Io(e.to_string()))?;
        }

        Ok(())
    }

    fn build_arguments(
        &self,
        version: &VersionDetails,
        minecraft_dir: &PathBuf,
        libraries_dir: &PathBuf,
        assets_dir: &PathBuf,
        versions_cache_dir: &PathBuf,
        settings: &LaunchSettings,
        auth_token: &str,
        username: &str,
        uuid: &str,
        supports_native_access: bool,
    ) -> Vec<String> {
        let mut args = Vec::new();

        args.push(format!("-Xms{}m", settings.memory_min_mb));
        args.push(format!("-Xmx{}m", settings.memory_max_mb));
        if supports_native_access {
            args.push("--enable-native-access=ALL-UNNAMED".to_string());
        }

        let append_jvm_arg = |arg_value: &serde_json::Value, out: &mut Vec<String>| {
            match arg_value {
                serde_json::Value::String(s) => {
                    out.extend(Self::replace_placeholders(
                        s,
                        username,
                        uuid,
                        auth_token,
                        version,
                        minecraft_dir,
                        assets_dir,
                        libraries_dir,
                        versions_cache_dir,
                        settings,
                    ));
                }
                serde_json::Value::Array(arr) => {
                    for a in arr {
                        if let serde_json::Value::String(s) = a {
                            out.extend(Self::replace_placeholders(
                                s,
                                username,
                                uuid,
                                auth_token,
                                version,
                                minecraft_dir,
                                assets_dir,
                                libraries_dir,
                                versions_cache_dir,
                                settings,
                            ));
                        }
                    }
                }
                _ => {}
            }
        };

        if let Some(arguments) = &version.arguments {
            for item in &arguments.jvm {
                match item {
                    serde_json::Value::String(_) | serde_json::Value::Array(_) => {
                        append_jvm_arg(item, &mut args);
                    }
                    serde_json::Value::Object(map) => {
                        if Self::evaluate_rule(
                            map.get("rules").unwrap_or(&serde_json::Value::Null),
                        ) {
                            if let Some(value) = map.get("value") {
                                append_jvm_arg(value, &mut args);
                            }
                        }
                    }
                    _ => {}
                }
            }
        } else {
            let parent = libraries_dir.parent().unwrap_or(libraries_dir);
            let natives_dir = parent.join("natives");
            std::fs::create_dir_all(&natives_dir).ok();
            args.push(format!("-Djava.library.path={}", natives_dir.display()));
        }

        for arg in &settings.extra_jvm_args {
            args.push(arg.clone());
        }

        let classpath = self.build_classpath(version, libraries_dir, versions_cache_dir);
        args.push("-cp".to_string());
        args.push(classpath);
        args.push(version.main_class.clone());

        let mut game_args = Vec::new();

        let append_game_arg = |arg_value: &serde_json::Value, out: &mut Vec<String>| {
            match arg_value {
                serde_json::Value::String(s) => {
                    out.extend(Self::replace_placeholders(
                        s,
                        username,
                        uuid,
                        auth_token,
                        version,
                        minecraft_dir,
                        assets_dir,
                        libraries_dir,
                        versions_cache_dir,
                        settings,
                    ));
                }
                serde_json::Value::Array(arr) => {
                    for a in arr {
                        if let serde_json::Value::String(s) = a {
                            out.extend(Self::replace_placeholders(
                                s,
                                username,
                                uuid,
                                auth_token,
                                version,
                                minecraft_dir,
                                assets_dir,
                                libraries_dir,
                                versions_cache_dir,
                                settings,
                            ));
                        }
                    }
                }
                _ => {}
            }
        };

        if let Some(arguments) = &version.arguments {
            for item in &arguments.game {
                match item {
                    serde_json::Value::String(_) | serde_json::Value::Array(_) => {
                        append_game_arg(item, &mut game_args);
                    }
                    serde_json::Value::Object(map) => {
                        if Self::evaluate_rule(
                            map.get("rules").unwrap_or(&serde_json::Value::Null),
                        ) {
                            if let Some(value) = map.get("value") {
                                append_game_arg(value, &mut game_args);
                            }
                        }
                    }
                    _ => {}
                }
            }
        } else if let Some(minecraft_args) = &version.minecraft_arguments {
            let parts = minecraft_args.split_whitespace().map(|s| s.to_string());
            for part in parts {
                game_args.extend(Self::replace_placeholders(
                    &part,
                    username,
                    uuid,
                    auth_token,
                    version,
                    minecraft_dir,
                    assets_dir,
                    libraries_dir,
                    versions_cache_dir,
                    settings,
                ));
            }
        }

        let mut filtered_game_args = Vec::new();
        let mut iter = game_args.into_iter().peekable();
        while let Some(arg) = iter.next() {
            if arg == "--quickPlayMultiplayer"
                || arg == "--quickPlayRealms"
                || arg == "--quickPlayPath"
                || arg == "--quickPlaySingleplayer"
                || arg == "--demo"
            {
                if let Some(next) = iter.peek() {
                    if !next.starts_with('-') {
                        iter.next();
                    }
                }
                continue;
            }
            filtered_game_args.push(arg);
        }
        game_args = filtered_game_args;

        let has_width = game_args.iter().any(|a| a.starts_with("--width"));
        let has_height = game_args.iter().any(|a| a.starts_with("--height"));
        if !has_width {
            game_args.push(format!("--width={}", settings.resolution_width));
        }
        if !has_height {
            game_args.push(format!("--height={}", settings.resolution_height));
        }
        if settings.fullscreen && !game_args.iter().any(|a| a == "--fullscreen") {
            game_args.push("--fullscreen".to_string());
        }

        for arg in &settings.extra_game_args {
            game_args.push(arg.clone());
        }

        args.extend(game_args);
        args
    }

    fn evaluate_rule(rules: &serde_json::Value) -> bool {
        if rules.is_null() {
            return true;
        }
        let rules = match rules.as_array() {
            Some(r) => r,
            None => return true,
        };
        let current_os = Self::current_os_key();
        let mut allowed = false;
        for rule in rules {
            let action = rule
                .get("action")
                .and_then(|a| a.as_str())
                .unwrap_or("allow");
            let os_name = rule
                .get("os")
                .and_then(|o| o.get("name"))
                .and_then(|n| n.as_str())
                .unwrap_or("");
            let os_match = os_name.is_empty() || os_name == current_os;
            if os_match && action == "allow" {
                allowed = true;
            } else if os_match && action == "disallow" {
                allowed = false;
            }
        }
        allowed
    }

    fn replace_placeholders(
        arg: &str,
        username: &str,
        uuid: &str,
        auth_token: &str,
        version: &VersionDetails,
        minecraft_dir: &PathBuf,
        assets_dir: &PathBuf,
        libraries_dir: &PathBuf,
        versions_cache_dir: &PathBuf,
        settings: &LaunchSettings,
    ) -> Vec<String> {
        let parent = libraries_dir.parent().unwrap_or(libraries_dir);
        let natives_dir = parent.join("natives");
        let classpath = Self::build_classpath_static(version, libraries_dir, versions_cache_dir);
        let result = arg
            .replace("${auth_player_name}", username)
            .replace("${version_name}", &version.id)
            .replace("${game_directory}", &minecraft_dir.display().to_string())
            .replace("${assets_root}", &assets_dir.display().to_string())
            .replace("${assets_index_name}", &version.asset_index.id)
            .replace("${auth_uuid}", uuid)
            .replace("${auth_access_token}", auth_token)
            .replace("${user_type}", "msa")
            .replace("${user_properties}", "{}")
            .replace("${version_type}", "release")
            .replace("${resolution_width}", &settings.resolution_width.to_string())
            .replace(
                "${resolution_height}",
                &settings.resolution_height.to_string(),
            )
            .replace("${natives_directory}", &natives_dir.display().to_string())
            .replace("${classpath}", &classpath);
        vec![result]
    }

    fn build_classpath_static(
        version: &VersionDetails,
        libraries_dir: &PathBuf,
        versions_cache_dir: &PathBuf,
    ) -> String {
        let mut parts = Vec::new();
        let client_jar = versions_cache_dir
            .join(&version.id)
            .join(format!("{}.jar", version.id));
        parts.push(client_jar.display().to_string());
        for library in &version.libraries {
            if let Some(artifact) = &library.downloads.artifact {
                parts.push(libraries_dir.join(&artifact.path).display().to_string());
            }
        }
        parts.join(&Self::classpath_separator().to_string())
    }

    async fn write_argfile(&self, dir: &PathBuf, args: &[String]) -> LaunchResult<PathBuf> {
        let path = dir.join(format!("launch_args_{}.txt", uuid::Uuid::new_v4()));
        let content = args.join("\n");
        tokio::fs::write(&path, content)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        Ok(path)
    }

    fn build_classpath(
        &self,
        version: &VersionDetails,
        libraries_dir: &PathBuf,
        versions_cache_dir: &PathBuf,
    ) -> String {
        Self::build_classpath_static(version, libraries_dir, versions_cache_dir)
    }

    fn maven_path_from_name(name: &str) -> String {
        let parts: Vec<&str> = name.split(':').collect();
        if parts.len() != 3 {
            return name.replace(':', "/");
        }
        let group = parts[0].replace('.', "/");
        let artifact = parts[1];
        let version = parts[2];
        format!(
            "{}/{}/{}/{}-{}.jar",
            group, artifact, version, artifact, version
        )
    }
}