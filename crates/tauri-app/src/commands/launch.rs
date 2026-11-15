use std::collections::VecDeque;

use mc_launch::launcher::{LaunchEvent, Launcher};
use mc_launch::models::{LaunchAccount, LaunchRequest, LaunchSettings, LoaderKind};
use pretupid_core::auth::oauth::OAuthClient;
use pretupid_core::instance::{Instance, InstanceMod};
use tauri::{AppHandle, Emitter, State};
use tokio::sync::{mpsc, watch};

use super::content::{content_file_path, load_instance};
use super::util::{self, ExpectedHash};
use crate::state::AppState;

const CRASH_CONTEXT_LINES: usize = 400;

#[tauri::command]
pub async fn fetch_versions(
    state: State<'_, AppState>,
) -> Result<Vec<mc_launch::models::VersionEntry>, String> {
    let manifest = state
        .resolver
        .fetch_manifest()
        .await
        .map_err(|e| e.to_string())?;
    Ok(manifest.versions)
}

fn loader_kind(instance: &Instance) -> LoaderKind {
    LoaderKind::parse(instance.mod_loader.as_str()).unwrap_or(LoaderKind::Vanilla)
}

async fn build_settings(
    state: &AppState,
    instance: &Instance,
    verify: bool,
) -> Result<LaunchSettings, String> {
    let instance_settings = state
        .instances
        .get_settings(instance.id)
        .await
        .map_err(|e| e.to_string())?;
    let global = state.settings.get().await.map_err(|e| e.to_string())?;
    Ok(LaunchSettings {
        memory_min_mb: instance_settings.memory_min_mb,
        memory_max_mb: instance_settings.memory_max_mb,
        resolution_width: instance_settings.resolution_width,
        resolution_height: instance_settings.resolution_height,
        fullscreen: instance_settings.fullscreen,
        java_path: instance_settings
            .java_path
            .clone()
            .filter(|p| !p.trim().is_empty())
            .or(global.default_java_path.clone()),
        auto_install_java: global.auto_install_java,
        extra_jvm_args: instance_settings
            .extra_jvm_args
            .iter()
            .cloned()
            .chain(super::pretupid_client::launch_properties(instance))
            .collect(),
        extra_game_args: instance_settings.extra_game_args.clone(),
        verify_files: verify,
        quick_play_server: instance_settings.quick_play_server.clone(),
    })
}

async fn sync_content(
    state: &AppState,
    instance: &Instance,
    entries: &[InstanceMod],
    app: &AppHandle,
) -> Vec<String> {
    let mut failures = Vec::new();
    let client = match util::http_client() {
        Ok(c) => c,
        Err(e) => return vec![e],
    };
    let instance_id = instance.id.to_string();
    for entry in entries {
        let path = match content_file_path(state, instance, entry) {
            Some(p) => p,
            None => continue,
        };
        if path.exists() {
            continue;
        }
        let url = match entry.file_url.as_deref().filter(|u| !u.is_empty()) {
            Some(u) => u,
            None => continue,
        };
        let _ = app.emit(
            "launch://progress",
            serde_json::json!({ "instance_id": instance_id, "current": 0, "total": 0, "message": format!("Restoring {}", entry.title) }),
        );
        let expected = ExpectedHash {
            sha1: entry.sha1.as_deref(),
            sha512: None,
        };
        if let Err(err) = util::download_verified(&client, url, &path, &expected).await {
            failures.push(format!("{}: {}", entry.title, err));
        }
    }
    failures
}

fn emit_event(app: &AppHandle, instance_id: &str, event: &LaunchEvent) {
    let (name, payload) = match event {
        LaunchEvent::Progress {
            current,
            total,
            message,
        } => (
            "launch://progress",
            serde_json::json!({ "instance_id": instance_id, "current": current, "total": total, "message": message }),
        ),
        LaunchEvent::Log { line } => (
            "launch://log",
            serde_json::json!({ "instance_id": instance_id, "line": line }),
        ),
        LaunchEvent::Started { pid } => (
            "launch://started",
            serde_json::json!({ "instance_id": instance_id, "pid": pid }),
        ),
        LaunchEvent::Failed { message } => (
            "launch://failed",
            serde_json::json!({ "instance_id": instance_id, "message": message }),
        ),
        LaunchEvent::Exited { code } => (
            "launch://exited",
            serde_json::json!({ "instance_id": instance_id, "code": code }),
        ),
    };
    let _ = app.emit(name, payload);
}

fn register_run(state: &AppState, instance_id: &str) -> Result<watch::Receiver<bool>, String> {
    let (cancel_tx, cancel_rx) = watch::channel(false);
    let mut guard = state
        .cancel_channels
        .lock()
        .map_err(|_| "state lock poisoned".to_string())?;
    if guard.contains_key(instance_id) {
        return Err("This instance is already running or being prepared.".to_string());
    }
    guard.insert(instance_id.to_string(), cancel_tx);
    Ok(cancel_rx)
}

fn unregister_run(state: &AppState, instance_id: &str) {
    if let Ok(mut guard) = state.cancel_channels.lock() {
        guard.remove(instance_id);
    }
}

fn latest_crash_report(game_dir: &std::path::Path, since: std::time::SystemTime) -> Option<String> {
    let dir = game_dir.join("crash-reports");
    let entries = std::fs::read_dir(dir).ok()?;
    entries
        .flatten()
        .filter_map(|e| {
            let modified = e.metadata().ok()?.modified().ok()?;
            (modified >= since).then(|| (modified, e.path()))
        })
        .max_by_key(|(m, _)| *m)
        .map(|(_, p)| p.to_string_lossy().to_string())
}

#[tauri::command]
pub async fn launch_instance(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_id: String,
    verify_files: Option<bool>,
) -> Result<(), String> {
    let instance = load_instance(&state, &instance_id).await?;

    let oauth = OAuthClient::new(state.pool.clone());
    let tokens = oauth
        .ensure_valid_token()
        .await
        .map_err(|e| e.to_string())?;
    if tokens.access_token.is_empty() {
        return Err("Your session expired. Please log out and sign in again.".to_string());
    }
    let profile = oauth
        .get_status()
        .await
        .map_err(|e| e.to_string())?
        .profile
        .ok_or_else(|| "No Minecraft profile found for this account.".to_string())?;

    match super::pretupid_client::update_if_outdated(&state, &instance).await {
        Ok(Some(version)) => tracing::info!("updated Pretupid Client to {version} before launch"),
        Ok(None) => {}
        Err(error) => tracing::warn!("Pretupid Client update check skipped: {error}"),
    }

    let settings = build_settings(&state, &instance, verify_files.unwrap_or(false)).await?;
    let cancel_rx = register_run(&state, &instance_id)?;

    let result = run_game(
        &app,
        &state,
        &instance,
        settings,
        tokens.access_token,
        profile.name,
        profile.id,
        cancel_rx,
    )
    .await;
    unregister_run(&state, &instance_id);
    result
}

#[allow(clippy::too_many_arguments)]
async fn run_game(
    app: &AppHandle,
    state: &AppState,
    instance: &Instance,
    settings: LaunchSettings,
    access_token: String,
    username: String,
    uuid: String,
    cancel_rx: watch::Receiver<bool>,
) -> Result<(), String> {
    let instance_id = instance.id.to_string();
    let entries = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;
    let failures = sync_content(state, instance, &entries, app).await;
    for failure in &failures {
        tracing::warn!("content sync failure: {failure}");
        emit_event(
            app,
            &instance_id,
            &LaunchEvent::Log {
                line: format!("[pretupid] Could not restore {failure}"),
            },
        );
    }

    let launcher = Launcher::new(
        state.profiles_dir.clone(),
        state.caches_dir.clone(),
        state.resolver.clone(),
    );
    let game_dir = launcher.game_dir(&instance.folder_name);
    let request = LaunchRequest {
        profile_name: instance.folder_name.clone(),
        version_id: instance.minecraft_version.clone(),
        loader: loader_kind(instance),
        loader_version: instance.loader_version.clone(),
        settings,
        account: LaunchAccount {
            username,
            uuid,
            access_token,
        },
    };

    let (tx, mut rx) = mpsc::unbounded_channel::<LaunchEvent>();
    let cancel_watch = cancel_rx.clone();
    let handle =
        tauri::async_runtime::spawn(async move { launcher.launch(request, tx, cancel_rx).await });

    let mut recent: VecDeque<String> = VecDeque::with_capacity(CRASH_CONTEXT_LINES);
    let mut started_at: Option<(
        chrono::DateTime<chrono::Utc>,
        std::time::Instant,
        std::time::SystemTime,
    )> = None;
    let mut failure_message: Option<String> = None;

    while let Some(event) = rx.recv().await {
        emit_event(app, &instance_id, &event);
        match &event {
            LaunchEvent::Log { line } => {
                if recent.len() == CRASH_CONTEXT_LINES {
                    recent.pop_front();
                }
                recent.push_back(line.clone());
            }
            LaunchEvent::Started { .. } => {
                started_at = Some((
                    chrono::Utc::now(),
                    std::time::Instant::now(),
                    std::time::SystemTime::now(),
                ));
            }
            LaunchEvent::Failed { message } => failure_message = Some(message.clone()),
            LaunchEvent::Exited { code } => {
                if let Some((started, instant, _)) = started_at {
                    let seconds = instant.elapsed().as_secs() as i64;
                    let _ = state
                        .instances
                        .record_play_session(instance.id, started, seconds)
                        .await;
                }
                let cancelled = *cancel_watch.borrow();
                let crashed = !cancelled && code.map(|c| c != 0).unwrap_or(false);
                if crashed {
                    let lines: Vec<String> = recent.iter().cloned().collect();
                    let hints = crate::crash::analyze(&lines);
                    let report =
                        started_at.and_then(|(_, _, since)| latest_crash_report(&game_dir, since));
                    let _ = app.emit(
                        "launch://crash",
                        serde_json::json!({
                            "instance_id": instance_id,
                            "code": code,
                            "hints": hints,
                            "crash_report": report,
                            "message": failure_message,
                        }),
                    );
                }
                break;
            }
            LaunchEvent::Progress { .. } => {}
        }
    }

    let _ = handle.await;
    let _ = app.emit("instances://changed", ());
    Ok(())
}

#[tauri::command]
pub async fn prepare_instance(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_id: String,
    verify_files: Option<bool>,
) -> Result<(), String> {
    let instance = load_instance(&state, &instance_id).await?;
    let settings = build_settings(&state, &instance, verify_files.unwrap_or(false)).await?;
    let cancel_rx = register_run(&state, &instance_id)?;

    let result: Result<(), String> = async {
        let entries = state
            .instances
            .list_mods(&instance_id)
            .await
            .map_err(|e| e.to_string())?;
        let failures = sync_content(&state, &instance, &entries, &app).await;
        let launcher = Launcher::new(
            state.profiles_dir.clone(),
            state.caches_dir.clone(),
            state.resolver.clone(),
        );
        let request = LaunchRequest {
            profile_name: instance.folder_name.clone(),
            version_id: instance.minecraft_version.clone(),
            loader: loader_kind(&instance),
            loader_version: instance.loader_version.clone(),
            settings,
            account: LaunchAccount {
                username: "Player".into(),
                uuid: "0".into(),
                access_token: "0".into(),
            },
        };
        let (tx, mut rx) = mpsc::unbounded_channel::<LaunchEvent>();
        let forward_app = app.clone();
        let forward_id = instance_id.clone();
        let forwarder = tauri::async_runtime::spawn(async move {
            while let Some(event) = rx.recv().await {
                emit_event(&forward_app, &forward_id, &event);
            }
        });
        let outcome = launcher.prepare(&request, &tx, &cancel_rx).await;
        drop(tx);
        let _ = forwarder.await;
        outcome.map_err(|e| e.to_string())?;
        if !failures.is_empty() {
            return Err(format!(
                "Game files are ready, but some content could not be restored: {}",
                failures.join("; ")
            ));
        }
        Ok(())
    }
    .await;

    unregister_run(&state, &instance_id);
    let _ = app.emit(
        "launch://prepared",
        serde_json::json!({ "instance_id": instance_id, "ok": result.is_ok(), "message": result.as_ref().err() }),
    );
    result
}

#[tauri::command]
pub async fn cancel_launch(state: State<'_, AppState>, instance_id: String) -> Result<(), String> {
    let guard = state
        .cancel_channels
        .lock()
        .map_err(|_| "state lock poisoned".to_string())?;
    if let Some(tx) = guard.get(&instance_id) {
        let _ = tx.send(true);
    }
    Ok(())
}

#[tauri::command]
pub async fn list_running_instances(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let guard = state
        .cancel_channels
        .lock()
        .map_err(|_| "state lock poisoned".to_string())?;
    Ok(guard.keys().cloned().collect())
}
