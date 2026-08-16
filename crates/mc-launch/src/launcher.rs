use std::path::PathBuf;
use std::process::Stdio;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use futures_util::stream::{self, StreamExt};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt};
use tokio::process::Command;
use tokio::sync::mpsc;
use tokio::sync::watch;

use crate::error::{LaunchError, LaunchResult};
use crate::models::{LaunchSettings, VersionDetails};
use crate::version::VersionResolver;

#[derive(Debug, Clone)]
pub enum LaunchEvent {
    Progress { current: u64, total: u64, message: String },
    Log { line: String },
    Started { pid: u32 },
    Exited { code: Option<i32> },
}

struct DownloadFile {
    url: String,
    path: PathBuf,
    size: u64,
    label: String,
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

    pub async fn launch(
        &self,
        profile_name: &str,
        version_id: &str,
        settings: &LaunchSettings,
        auth_token: &str,
        username: &str,
        uuid: &str,
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
                    .send(LaunchEvent::Exited { code: Some(1) })
                    .ok();
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
        event_tx: mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        let profile_dir = self.profiles_dir.join(profile_name);
        std::fs::create_dir_all(&profile_dir)
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        let libraries_dir = self.caches_dir.join("libraries");
        let assets_dir = self.caches_dir.join("assets");
        let versions_cache_dir = self.caches_dir.join("versions");
        std::fs::create_dir_all(&libraries_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        std::fs::create_dir_all(&assets_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        std::fs::create_dir_all(&versions_cache_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let version_details = self
            .resolver
            .get_version_details(version_id)
            .await?;

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
                label: "Minecraft client".to_string(),
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
                        label: library.name.clone(),
                    });
                }
            }
        }

        let indexes_dir = assets_dir.join("indexes");
        std::fs::create_dir_all(&indexes_dir).map_err(|e| LaunchError::Io(e.to_string()))?;
        let index_path = indexes_dir.join(format!("{}.json", version_details.asset_index.id));
        let index_downloaded = !index_path.exists();
        if index_downloaded {
            files.push(DownloadFile {
                url: version_details.asset_index.url.clone(),
                path: index_path.clone(),
                size: version_details.asset_index.size,
                label: "asset index".to_string(),
            });
        }

        self.download_files(files, &event_tx, &cancel_rx).await?;

        let index_content = tokio::fs::read_to_string(&index_path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        let asset_index: crate::models::AssetIndexDetails =
            serde_json::from_str(&index_content)
                .map_err(|e| LaunchError::Parse(e.to_string()))?;

        let mut asset_files = Vec::new();
        let objects_dir = assets_dir.join("objects");
        std::fs::create_dir_all(&objects_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        for (name, object) in &asset_index.objects {
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
                    label: name.clone(),
                });
            }
        }

        self.download_files(asset_files, &event_tx, &cancel_rx)
            .await?;

        self.prepare_legacy_assets(&version_details, &assets_dir).await?;

        self.extract_natives(&version_details, &libraries_dir).await?;

        let java_major = version_details
            .java_version
            .as_ref()
            .map(|j| j.major_version)
            .unwrap_or(8);
        let supports_native_access = java_major >= 17;

        let assets_for_launch = if version_details.assets == "legacy" || version_details.assets == "pre-1.6" {
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
        command
            .args(&args)
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
            Self::report_progress(reporter_downloaded, total_size, reporter_tx, reporter_cancel).await;
        });

        let download_tasks = stream::iter(files.into_iter().map(|file| {
            let downloaded = downloaded.clone();
            let event_tx = event_tx.clone();
            let cancel_rx = cancel_rx.clone();
            async move {
                self.download_file(
                    file.url.clone(),
                    file.path.clone(),
                    file.size,
                    file.label.clone(),
                    downloaded.clone(),
                    event_tx,
                    cancel_rx,
                )
                .await
            }
        }))
        .buffer_unordered(8)
        .collect::<Vec<Result<(), LaunchError>>>()
        .await;

        for result in download_tasks {
            if let Err(e) = result {
                return Err(e);
            }
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
                    if current >= total_size {
                        return;
                    }
                    let message = format!(
                        "Downloading Minecraft ({} / {} MB)",
                        current / 1024 / 1024,
                        total_size / 1024 / 1024
                    );
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
        &self,
        url: String,
        path: PathBuf,
        _total_size: u64,
        _label: String,
        downloaded: Arc<AtomicU64>,
        _event_tx: mpsc::UnboundedSender<LaunchEvent>,
        mut cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| LaunchError::Io(e.to_string()))?;
        }

        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(300))
            .build()
            .map_err(|e| LaunchError::Network(e.to_string()))?;

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
        let asset_index: crate::models::AssetIndexDetails =
            serde_json::from_str(&index_content)
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
        let natives_dir = libraries_dir.parent().unwrap().join("natives");
        std::fs::create_dir_all(&natives_dir)
            .map_err(|e| LaunchError::Io(e.to_string()))?;

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

            if native_sources.is_empty() {
                if let Some(artifact) = &library.downloads.artifact {
                    native_sources.push(artifact);
                }
            }

            for native_artifact in native_sources {
                let jar_path = libraries_dir.join(&native_artifact.path);
                if !jar_path.exists() {
                    self.download_file(
                        native_artifact.url.clone(),
                        jar_path.clone(),
                        native_artifact.size,
                        format!("Downloading native {}", library.name),
                        Arc::new(AtomicU64::new(0)),
                        mpsc::unbounded_channel().0,
                        watch::channel(false).1,
                    )
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
        let file = std::fs::File::open(jar_path)
            .map_err(|e| LaunchError::Io(e.to_string()))?;

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

            let ext = std::path::Path::new(&file_name)
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_lowercase();

            if ext != "dll" && ext != "so" && ext != "dylib" && ext != "jnilib" {
                continue;
            }

            let out_path = natives_dir.join(&file_name);
            let mut out_file = std::fs::File::create(&out_path)
                .map_err(|e| LaunchError::Io(e.to_string()))?;

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

        for arg in &settings.extra_jvm_args {
            args.push(arg.clone());
        }

        let natives_dir = libraries_dir.parent().unwrap().join("natives");
        std::fs::create_dir_all(&natives_dir).ok();
        args.push(format!(
            "-Djava.library.path={}",
            natives_dir.display()
        ));

        let classpath = self.build_classpath(version, libraries_dir, versions_cache_dir);
        args.push("-cp".to_string());
        args.push(classpath);
        args.push(version.main_class.clone());

        args.push(format!("--gameDir={}", minecraft_dir.display()));
        args.push(format!("--assetsDir={}", assets_dir.display()));
        args.push(format!("--assetIndex={}", version.asset_index.id));
        args.push(format!("--version={}", version.id));

        args.push(format!("--width={}", settings.resolution_width));
        args.push(format!("--height={}", settings.resolution_height));

        if settings.fullscreen {
            args.push("--fullscreen".to_string());
        }

        args.push(format!("--username={}", username));
        args.push(format!("--uuid={}", uuid));
        args.push(format!("--accessToken={}", auth_token));
        args.push("--versionType=release".to_string());

        for arg in &settings.extra_game_args {
            args.push(arg.clone());
        }

        args
    }

    fn build_classpath(
        &self,
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
                let path = libraries_dir.join(&artifact.path);
                parts.push(path.display().to_string());
            }
        }

        parts.join(";")
    }
}