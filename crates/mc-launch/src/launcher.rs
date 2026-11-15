use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::process::Stdio;

use tokio::io::AsyncBufReadExt;
use tokio::sync::{mpsc, watch};

use crate::download::{self, DownloadTask};
use crate::error::{LaunchError, LaunchResult};
use crate::java::JavaRuntime;
use crate::libraries::{self, is_safe_relative_path};
use crate::loaders::{fabric, forge};
use crate::models::{AssetIndexDetails, LaunchRequest, LaunchSettings, LoaderKind, VersionDetails};
use crate::process;
use crate::version::VersionResolver;

#[derive(Debug, Clone)]
pub enum LaunchEvent {
    Progress {
        current: u64,
        total: u64,
        message: String,
    },
    Log {
        line: String,
    },
    Started {
        pid: u32,
    },
    Exited {
        code: Option<i32>,
    },
    Failed {
        message: String,
    },
}

pub struct PreparedLaunch {
    pub java_path: PathBuf,
    pub java_major: u32,
    pub args: Vec<String>,
    pub game_dir: PathBuf,
}

struct Paths {
    game_dir: PathBuf,
    libraries: PathBuf,
    assets: PathBuf,
    versions: PathBuf,
    natives: PathBuf,
}

pub struct Launcher {
    resolver: VersionResolver,
    profiles_dir: PathBuf,
    caches_dir: PathBuf,
}

fn send(event_tx: &mpsc::UnboundedSender<LaunchEvent>, event: LaunchEvent) {
    event_tx.send(event).ok();
}

fn stage(event_tx: &mpsc::UnboundedSender<LaunchEvent>, message: impl Into<String>) {
    send(
        event_tx,
        LaunchEvent::Progress {
            current: 0,
            total: 0,
            message: message.into(),
        },
    );
}

async fn wait_for_cancel(mut cancel_rx: watch::Receiver<bool>) {
    loop {
        if *cancel_rx.borrow() {
            return;
        }
        if cancel_rx.changed().await.is_err() {
            std::future::pending::<()>().await;
        }
    }
}

fn quote_argfile(arg: &str) -> String {
    let escaped = arg.replace('\\', "\\\\").replace('"', "\\\"");
    format!("\"{}\"", escaped)
}

impl Launcher {
    pub fn new(profiles_dir: PathBuf, caches_dir: PathBuf, resolver: VersionResolver) -> Self {
        Self {
            resolver,
            profiles_dir,
            caches_dir,
        }
    }

    pub fn game_dir(&self, profile_name: &str) -> PathBuf {
        self.profiles_dir.join(profile_name)
    }

    fn paths(&self, profile_name: &str, version_id: &str) -> Paths {
        Paths {
            game_dir: self.game_dir(profile_name),
            libraries: self.caches_dir.join("libraries"),
            assets: self.caches_dir.join("assets"),
            versions: self.caches_dir.join("versions"),
            natives: self.caches_dir.join("natives").join(version_id),
        }
    }

    pub async fn launch(
        &self,
        request: LaunchRequest,
        event_tx: mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        let result = self.launch_inner(&request, &event_tx, cancel_rx).await;
        if let Err(e) = &result {
            send(
                &event_tx,
                LaunchEvent::Log {
                    line: format!("Launch failed: {}", e),
                },
            );
            send(
                &event_tx,
                LaunchEvent::Failed {
                    message: e.to_string(),
                },
            );
            send(&event_tx, LaunchEvent::Exited { code: Some(1) });
        }
        result
    }

    async fn launch_inner(
        &self,
        request: &LaunchRequest,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        let prepared = self.prepare(request, event_tx, &cancel_rx).await?;
        if *cancel_rx.borrow() {
            return Err(LaunchError::Cancelled);
        }

        stage(event_tx, "Starting Minecraft");
        Self::cleanup_argfiles(&prepared.game_dir).await;

        let mut argfile: Option<PathBuf> = None;
        let mut command = process::tokio_command(&prepared.java_path);
        if prepared.java_major >= 9 {
            let path = prepared
                .game_dir
                .join(format!("launch_args_{}.txt", uuid::Uuid::new_v4()));
            let content: Vec<String> = prepared.args.iter().map(|a| quote_argfile(a)).collect();
            tokio::fs::write(&path, content.join("\n")).await?;
            command.arg(format!("@{}", path.display()));
            argfile = Some(path);
        } else {
            command.args(&prepared.args);
        }
        command
            .current_dir(&prepared.game_dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .stdin(Stdio::null())
            .kill_on_drop(false);

        let mut child = match command.spawn() {
            Ok(c) => c,
            Err(e) => {
                if let Some(path) = &argfile {
                    let _ = tokio::fs::remove_file(path).await;
                }
                return Err(LaunchError::LaunchFailed(format!(
                    "could not start Java at {}: {}",
                    prepared.java_path.display(),
                    e
                )));
            }
        };

        if let Some(pid) = child.id() {
            send(event_tx, LaunchEvent::Started { pid });
        }

        if let Some(path) = argfile.clone() {
            tokio::spawn(async move {
                tokio::time::sleep(std::time::Duration::from_secs(20)).await;
                let _ = tokio::fs::remove_file(path).await;
            });
        }

        if let Some(stdout) = child.stdout.take() {
            let tx = event_tx.clone();
            tokio::spawn(async move {
                let mut reader = tokio::io::BufReader::new(stdout).lines();
                while let Ok(Some(line)) = reader.next_line().await {
                    tx.send(LaunchEvent::Log { line }).ok();
                }
            });
        }
        if let Some(stderr) = child.stderr.take() {
            let tx = event_tx.clone();
            tokio::spawn(async move {
                let mut reader = tokio::io::BufReader::new(stderr).lines();
                while let Ok(Some(line)) = reader.next_line().await {
                    tx.send(LaunchEvent::Log { line }).ok();
                }
            });
        }

        let status = tokio::select! {
            status = child.wait() => status.map_err(|e| LaunchError::LaunchFailed(e.to_string()))?,
            _ = wait_for_cancel(cancel_rx.clone()) => {
                let _ = child.kill().await;
                child.wait().await.map_err(|e| LaunchError::LaunchFailed(e.to_string()))?
            }
        };

        if let Some(path) = &argfile {
            let _ = tokio::fs::remove_file(path).await;
        }
        send(
            event_tx,
            LaunchEvent::Exited {
                code: status.code(),
            },
        );
        Ok(())
    }

    async fn cleanup_argfiles(game_dir: &Path) {
        if let Ok(mut entries) = tokio::fs::read_dir(game_dir).await {
            while let Ok(Some(entry)) = entries.next_entry().await {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with("launch_args_") && name.ends_with(".txt") {
                    let _ = tokio::fs::remove_file(entry.path()).await;
                }
            }
        }
    }

    pub async fn prepare(
        &self,
        request: &LaunchRequest,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<PreparedLaunch> {
        let paths = self.paths(&request.profile_name, &request.version_id);
        for dir in [
            &paths.game_dir,
            &paths.libraries,
            &paths.assets,
            &paths.versions,
            &paths.natives,
        ] {
            tokio::fs::create_dir_all(dir).await?;
        }
        if request.loader != LoaderKind::Vanilla {
            tokio::fs::create_dir_all(paths.game_dir.join("mods")).await?;
        }

        let client = download::http_client()?;
        let settings = &request.settings;
        let verify = settings.verify_files;

        stage(
            event_tx,
            format!("Resolving Minecraft {}", request.version_id),
        );
        let mut version = self
            .resolver
            .get_version_details(&request.version_id)
            .await?;

        let required_java = version
            .java_version
            .as_ref()
            .map(|j| j.major_version)
            .unwrap_or(8);
        let java_path = self
            .resolve_java(settings, required_java, event_tx, cancel_rx)
            .await?;
        let java_major = JavaRuntime::java_major(&java_path).unwrap_or(required_java);
        if java_major < required_java {
            send(
                event_tx,
                LaunchEvent::Log {
                    line: format!(
                        "Warning: Minecraft {} expects Java {} but Java {} is configured.",
                        request.version_id, required_java, java_major
                    ),
                },
            );
        }

        let client_jar = paths
            .versions
            .join(&version.id)
            .join(format!("{}.jar", version.id));
        let client_download = &version.downloads.client;
        if client_download.url.is_empty() {
            if !client_jar.exists() {
                return Err(LaunchError::NotFound(format!(
                    "no client download for {}",
                    version.id
                )));
            }
        } else {
            let sha1 = Some(client_download.sha1.clone()).filter(|s| !s.is_empty());
            if !download::file_is_valid(&client_jar, sha1.as_deref(), client_download.size, verify)
                .await
            {
                let task = DownloadTask::new(
                    client_download.url.clone(),
                    client_jar.clone(),
                    sha1,
                    client_download.size,
                );
                download::download_all(
                    &client,
                    vec![task],
                    "Downloading Minecraft",
                    event_tx,
                    cancel_rx,
                    1,
                )
                .await?;
            }
        }

        match request.loader {
            LoaderKind::Vanilla => {}
            LoaderKind::Fabric | LoaderKind::Quilt => {
                stage(event_tx, format!("Preparing {}", request.loader.label()));
                let profile = fabric::resolve_profile(
                    &client,
                    request.loader,
                    &version.id,
                    request.loader_version.as_deref(),
                    &self.caches_dir,
                )
                .await?;
                send(
                    event_tx,
                    LaunchEvent::Log {
                        line: format!("Using {} profile {}", request.loader.label(), profile.id),
                    },
                );
                libraries::merge_loader_profile(&mut version, profile);
            }
            LoaderKind::Forge | LoaderKind::NeoForge => {
                let loader_version = request.loader_version.clone().unwrap_or_default();
                let ctx = forge::ForgeInstallContext {
                    client: &client,
                    caches_dir: &self.caches_dir,
                    libraries_dir: &paths.libraries,
                    client_jar: &client_jar,
                    mc_version: &version.id,
                    java_path: &java_path,
                    verify,
                    event_tx,
                    cancel_rx,
                };
                let profile = forge::install(&ctx, request.loader, &loader_version).await?;
                libraries::merge_loader_profile(&mut version, profile);
            }
        }

        for lib in version.libraries.iter_mut() {
            libraries::normalize_library(lib);
        }
        version.libraries.retain(libraries::library_allowed);

        self.download_libraries(&client, &version, &paths, verify, event_tx, cancel_rx)
            .await?;
        let asset_index = self
            .download_assets(&client, &version, &paths, verify, event_tx, cancel_rx)
            .await?;

        stage(event_tx, "Preparing game files");
        self.extract_natives(&version, &paths)?;
        let assets_root = self
            .prepare_legacy_assets(&version, &asset_index, &paths)
            .await?;

        let args = self.build_arguments(&version, &paths, &assets_root, request, java_major);
        Ok(PreparedLaunch {
            java_path,
            java_major,
            args,
            game_dir: paths.game_dir,
        })
    }

    async fn resolve_java(
        &self,
        settings: &LaunchSettings,
        required: u32,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<PathBuf> {
        if let Some(custom) = settings
            .java_path
            .as_deref()
            .map(str::trim)
            .filter(|p| !p.is_empty())
        {
            let path = PathBuf::from(custom);
            if path.is_file() {
                return Ok(path);
            }
            send(
                event_tx,
                LaunchEvent::Log {
                    line: format!(
                        "Configured Java path {} does not exist; detecting automatically.",
                        custom
                    ),
                },
            );
        }
        JavaRuntime::detect_for_version(
            required,
            settings.auto_install_java,
            self.caches_dir.join("java"),
            event_tx.clone(),
            cancel_rx.clone(),
        )
        .await
    }

    async fn download_libraries(
        &self,
        client: &reqwest::Client,
        version: &VersionDetails,
        paths: &Paths,
        verify: bool,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        stage(event_tx, "Checking libraries");
        let mut tasks: Vec<DownloadTask> = Vec::new();
        let mut queued: HashSet<PathBuf> = HashSet::new();
        let mut missing_local: Vec<String> = Vec::new();

        let mut consider =
            |artifact: &crate::models::Artifact, name: &str| -> Option<DownloadTask> {
                if !is_safe_relative_path(&artifact.path) {
                    return None;
                }
                let path = paths.libraries.join(&artifact.path);
                if !queued.insert(path.clone()) {
                    return None;
                }
                if artifact.url.is_empty() {
                    if !path.exists() {
                        missing_local.push(name.to_string());
                    }
                    return None;
                }
                let sha1 = Some(artifact.sha1.clone()).filter(|s| !s.is_empty());
                Some(DownloadTask::new(
                    artifact.url.clone(),
                    path,
                    sha1,
                    artifact.size,
                ))
            };

        let mut candidates = Vec::new();
        for library in &version.libraries {
            if let Some(artifact) = &library.downloads.artifact {
                if let Some(task) = consider(artifact, &library.name) {
                    candidates.push(task);
                }
            }
            if let Some(classifier) = Self::native_classifier(library) {
                if let Some(artifact) = library
                    .downloads
                    .classifiers
                    .as_ref()
                    .and_then(|c| c.get(&classifier))
                {
                    if let Some(task) = consider(artifact, &library.name) {
                        candidates.push(task);
                    }
                }
            }
        }

        if !missing_local.is_empty() {
            return Err(LaunchError::NotFound(format!(
                "loader files are missing ({}). Use Repair on this instance to reinstall the loader.",
                missing_local.join(", ")
            )));
        }

        tasks.extend(download::retain_missing(candidates, verify).await);
        download::download_all(
            client,
            tasks,
            "Downloading libraries",
            event_tx,
            cancel_rx,
            16,
        )
        .await
    }

    async fn download_assets(
        &self,
        client: &reqwest::Client,
        version: &VersionDetails,
        paths: &Paths,
        verify: bool,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &watch::Receiver<bool>,
    ) -> LaunchResult<AssetIndexDetails> {
        let indexes_dir = paths.assets.join("indexes");
        tokio::fs::create_dir_all(&indexes_dir).await?;
        let index = &version.asset_index;
        if index.id.is_empty() || !is_safe_relative_path(&index.id) {
            return Err(LaunchError::Parse(
                "version has no valid asset index".to_string(),
            ));
        }
        let index_path = indexes_dir.join(format!("{}.json", index.id));
        let sha1 = Some(index.sha1.clone()).filter(|s| !s.is_empty());
        if !download::file_is_valid(&index_path, sha1.as_deref(), index.size, true).await {
            let task = DownloadTask::new(index.url.clone(), index_path.clone(), sha1, index.size);
            download::download_single(client, &task, cancel_rx).await?;
        }

        let content = tokio::fs::read_to_string(&index_path).await?;
        let details: AssetIndexDetails = serde_json::from_str(&content)
            .map_err(|e| LaunchError::Parse(format!("asset index: {}", e)))?;

        stage(event_tx, "Checking assets");
        let objects_dir = paths.assets.join("objects");
        let mut seen: HashSet<&str> = HashSet::new();
        let mut candidates = Vec::new();
        for object in details.objects.values() {
            if object.hash.len() < 3
                || !object.hash.chars().all(|c| c.is_ascii_hexdigit())
                || !seen.insert(&object.hash)
            {
                continue;
            }
            let prefix = &object.hash[0..2];
            let path = objects_dir.join(prefix).join(&object.hash);
            candidates.push(DownloadTask::new(
                format!(
                    "https://resources.download.minecraft.net/{}/{}",
                    prefix, object.hash
                ),
                path,
                Some(object.hash.clone()),
                object.size,
            ));
        }
        let tasks = download::retain_missing(candidates, verify).await;
        download::download_all(client, tasks, "Downloading assets", event_tx, cancel_rx, 32)
            .await?;
        Ok(details)
    }

    async fn prepare_legacy_assets(
        &self,
        version: &VersionDetails,
        index: &AssetIndexDetails,
        paths: &Paths,
    ) -> LaunchResult<PathBuf> {
        let legacy = index.is_virtual
            || index.map_to_resources
            || version.assets == "legacy"
            || version.assets == "pre-1.6";
        if !legacy {
            return Ok(paths.assets.clone());
        }
        let target_root = if index.map_to_resources {
            paths.game_dir.join("resources")
        } else {
            paths.assets.join("virtual").join(&version.asset_index.id)
        };
        for (name, object) in &index.objects {
            if !is_safe_relative_path(name) || object.hash.len() < 3 {
                continue;
            }
            let source = paths
                .assets
                .join("objects")
                .join(&object.hash[0..2])
                .join(&object.hash);
            let target = target_root.join(name);
            if !source.exists() {
                continue;
            }
            if let Ok(meta) = tokio::fs::metadata(&target).await {
                if meta.len() == object.size {
                    continue;
                }
            }
            if let Some(parent) = target.parent() {
                tokio::fs::create_dir_all(parent).await?;
            }
            tokio::fs::copy(&source, &target).await?;
        }
        Ok(target_root)
    }

    fn native_classifier(library: &crate::models::Library) -> Option<String> {
        let natives = library.natives.as_ref()?;
        let template = natives.get(libraries::current_os())?;
        let arch = if cfg!(target_pointer_width = "64") {
            "64"
        } else {
            "32"
        };
        Some(template.replace("${arch}", arch))
    }

    fn extract_natives(&self, version: &VersionDetails, paths: &Paths) -> LaunchResult<()> {
        std::fs::create_dir_all(&paths.natives)?;
        for library in &version.libraries {
            let classifier = match Self::native_classifier(library) {
                Some(c) => c,
                None => continue,
            };
            let artifact = match library
                .downloads
                .classifiers
                .as_ref()
                .and_then(|c| c.get(&classifier))
            {
                Some(a) => a,
                None => continue,
            };
            if !is_safe_relative_path(&artifact.path) {
                continue;
            }
            let jar = paths.libraries.join(&artifact.path);
            if jar.exists() {
                Self::extract_jar_natives(&jar, &paths.natives)?;
            }
        }
        Ok(())
    }

    fn extract_jar_natives(jar_path: &Path, natives_dir: &Path) -> LaunchResult<()> {
        let file = std::fs::File::open(jar_path)?;
        let mut archive = zip::ZipArchive::new(file)
            .map_err(|e| LaunchError::Parse(format!("invalid jar archive: {}", e)))?;
        for i in 0..archive.len() {
            let mut entry = archive
                .by_index(i)
                .map_err(|e| LaunchError::Parse(e.to_string()))?;
            let name = match entry.enclosed_name() {
                Some(n) => n,
                None => continue,
            };
            if name.starts_with("META-INF") {
                continue;
            }
            let file_name = match name.file_name().and_then(|n| n.to_str()) {
                Some(f) => f.to_string(),
                None => continue,
            };
            let ext = Path::new(&file_name)
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_lowercase();
            if !matches!(ext.as_str(), "dll" | "so" | "dylib" | "jnilib") {
                continue;
            }
            let out_path = natives_dir.join(&file_name);
            if let Ok(meta) = std::fs::metadata(&out_path) {
                if meta.len() == entry.size() {
                    continue;
                }
            }
            let mut out_file = std::fs::File::create(&out_path)?;
            std::io::copy(&mut entry, &mut out_file)?;
        }
        Ok(())
    }

    fn classpath(&self, version: &VersionDetails, paths: &Paths) -> String {
        let mut seen: HashSet<PathBuf> = HashSet::new();
        let mut parts: Vec<String> = Vec::new();
        for library in &version.libraries {
            if let Some(artifact) = &library.downloads.artifact {
                if !is_safe_relative_path(&artifact.path) {
                    continue;
                }
                let path = paths.libraries.join(&artifact.path);
                if seen.insert(path.clone()) {
                    parts.push(path.display().to_string());
                }
            }
        }
        let client_jar = paths
            .versions
            .join(&version.id)
            .join(format!("{}.jar", version.id));
        parts.push(client_jar.display().to_string());
        let separator = if cfg!(windows) { ";" } else { ":" };
        parts.join(separator)
    }

    fn build_arguments(
        &self,
        version: &VersionDetails,
        paths: &Paths,
        assets_root: &Path,
        request: &LaunchRequest,
        java_major: u32,
    ) -> Vec<String> {
        let settings = &request.settings;
        let account = &request.account;
        let classpath = self.classpath(version, paths);

        let quick_play = settings
            .quick_play_server
            .as_deref()
            .map(str::trim)
            .filter(|s| {
                !s.is_empty()
                    && s.chars()
                        .all(|c| c.is_ascii_alphanumeric() || ".-:_[]".contains(c))
            });

        let mut features: HashMap<&str, bool> = HashMap::new();
        features.insert("has_custom_resolution", true);
        features.insert("is_demo_user", false);
        features.insert("has_quick_plays_support", false);
        features.insert("is_quick_play_singleplayer", false);
        features.insert("is_quick_play_realms", false);
        features.insert("is_quick_play_multiplayer", quick_play.is_some());

        let mut values: HashMap<&str, String> = HashMap::new();
        values.insert("auth_player_name", account.username.clone());
        values.insert("version_name", version.id.clone());
        values.insert("game_directory", paths.game_dir.display().to_string());
        values.insert("assets_root", assets_root.display().to_string());
        values.insert("game_assets", assets_root.display().to_string());
        values.insert("assets_index_name", version.asset_index.id.clone());
        values.insert("auth_uuid", account.uuid.clone());
        values.insert("auth_access_token", account.access_token.clone());
        values.insert(
            "auth_session",
            format!("token:{}:{}", account.access_token, account.uuid),
        );
        values.insert("user_type", "msa".to_string());
        values.insert("user_properties", "{}".to_string());
        values.insert("version_type", version.version_type.clone());
        values.insert("resolution_width", settings.resolution_width.to_string());
        values.insert("resolution_height", settings.resolution_height.to_string());
        values.insert("natives_directory", paths.natives.display().to_string());
        values.insert("classpath", classpath.clone());
        values.insert(
            "classpath_separator",
            if cfg!(windows) { ";" } else { ":" }.to_string(),
        );
        values.insert("library_directory", paths.libraries.display().to_string());
        values.insert("launcher_name", "pretupid".to_string());
        values.insert("launcher_version", env!("CARGO_PKG_VERSION").to_string());
        if let Some(server) = quick_play {
            values.insert("quickPlayMultiplayer", server.to_string());
        }

        let substitute = |raw: &str| -> String {
            let mut out = raw.to_string();
            for (key, value) in &values {
                let token = format!("${{{}}}", key);
                if out.contains(&token) {
                    out = out.replace(&token, value);
                }
            }
            out
        };

        let expand = |items: &[serde_json::Value], out: &mut Vec<String>| {
            for item in items {
                let value = match item {
                    serde_json::Value::Object(map) => {
                        if !libraries::json_rules_allow(map.get("rules"), &features) {
                            continue;
                        }
                        match map.get("value") {
                            Some(v) => v.clone(),
                            None => continue,
                        }
                    }
                    other => other.clone(),
                };
                match value {
                    serde_json::Value::String(s) => out.push(substitute(&s)),
                    serde_json::Value::Array(arr) => {
                        for v in arr {
                            if let serde_json::Value::String(s) = v {
                                out.push(substitute(&s));
                            }
                        }
                    }
                    _ => {}
                }
            }
        };

        let mut jvm: Vec<String> = vec![
            format!(
                "-Xms{}m",
                settings.memory_min_mb.min(settings.memory_max_mb)
            ),
            format!("-Xmx{}m", settings.memory_max_mb),
        ];
        if java_major >= 17 {
            jvm.push("--enable-native-access=ALL-UNNAMED".to_string());
        }

        let mut json_jvm: Vec<String> = Vec::new();
        if let Some(arguments) = &version.arguments {
            expand(&arguments.jvm, &mut json_jvm);
        }
        if json_jvm.is_empty() {
            json_jvm.push(format!("-Djava.library.path={}", paths.natives.display()));
            json_jvm.push("-cp".to_string());
            json_jvm.push(classpath.clone());
        } else {
            if !json_jvm
                .iter()
                .any(|a| a.starts_with("-Djava.library.path="))
            {
                json_jvm.insert(
                    0,
                    format!("-Djava.library.path={}", paths.natives.display()),
                );
            }
            if !json_jvm.iter().any(|a| a == "-cp" || a == "-classpath") {
                json_jvm.push("-cp".to_string());
                json_jvm.push(classpath.clone());
            }
        }
        jvm.extend(json_jvm);
        jvm.extend(
            settings
                .extra_jvm_args
                .iter()
                .filter(|a| !a.trim().is_empty())
                .cloned(),
        );
        jvm.push(version.main_class.clone());

        let mut game: Vec<String> = Vec::new();
        match (&version.arguments, &version.minecraft_arguments) {
            (_, Some(legacy))
                if version
                    .arguments
                    .as_ref()
                    .map(|a| a.game.is_empty())
                    .unwrap_or(true) =>
            {
                for part in legacy.split_whitespace() {
                    game.push(substitute(part));
                }
            }
            (Some(arguments), _) => expand(&arguments.game, &mut game),
            _ => {}
        }

        let mut cleaned: Vec<String> = Vec::with_capacity(game.len());
        let mut iter = game.into_iter().peekable();
        while let Some(arg) = iter.next() {
            if arg.contains("${") {
                continue;
            }
            if arg.starts_with("--") {
                if let Some(next) = iter.peek() {
                    if next.contains("${") {
                        iter.next();
                        continue;
                    }
                }
            }
            cleaned.push(arg);
        }

        if !cleaned.iter().any(|a| a == "--width") {
            cleaned.push("--width".to_string());
            cleaned.push(settings.resolution_width.to_string());
        }
        if !cleaned.iter().any(|a| a == "--height") {
            cleaned.push("--height".to_string());
            cleaned.push(settings.resolution_height.to_string());
        }
        if settings.fullscreen && !cleaned.iter().any(|a| a == "--fullscreen") {
            cleaned.push("--fullscreen".to_string());
        }
        if let Some(server) = quick_play {
            if !cleaned.iter().any(|a| a == "--quickPlayMultiplayer") {
                let (host, port) = match server.rsplit_once(':') {
                    Some((h, p)) if p.chars().all(|c| c.is_ascii_digit()) && !h.contains(':') => {
                        (h.to_string(), p.to_string())
                    }
                    _ => (server.to_string(), "25565".to_string()),
                };
                cleaned.push("--server".to_string());
                cleaned.push(host);
                cleaned.push("--port".to_string());
                cleaned.push(port);
            }
        }
        cleaned.extend(
            settings
                .extra_game_args
                .iter()
                .filter(|a| !a.trim().is_empty())
                .cloned(),
        );

        jvm.extend(cleaned);
        jvm
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn argfile_quoting() {
        assert_eq!(
            quote_argfile(r"C:\Users\John Doe\x"),
            r#""C:\\Users\\John Doe\\x""#
        );
        assert_eq!(quote_argfile(r#"a"b"#), r#""a\"b""#);
    }
}
