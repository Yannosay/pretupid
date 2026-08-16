use mc_launch::launcher::{LaunchEvent, Launcher};
use mc_launch::models::LaunchSettings;
use pretupid_core::auth::oauth::OAuthClient;
use tauri::{AppHandle, Emitter, State};
use tokio::sync::{mpsc, watch};

use crate::state::AppState;

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

#[tauri::command]
pub async fn launch_instance(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_id: String,
    version_id: String,
    mut settings: LaunchSettings,
) -> Result<(), String> {
    let oauth = OAuthClient::new(state.pool.clone());
    let tokens = oauth
        .get_tokens()
        .await
        .map_err(|e| e.to_string())?;

    let access_token = tokens.access_token.clone();
    if access_token.is_empty() {
        return Err("Stored token is empty. Please logout and login again.".to_string());
    }

    let auth_status = oauth
        .get_status()
        .await
        .map_err(|e| e.to_string())?;

    let profile = auth_status
        .profile
        .ok_or_else(|| "Minecraft profile not found".to_string())?;

    let instance = state
        .instances
        .get_by_id(uuid::Uuid::parse_str(&instance_id).map_err(|e| e.to_string())?)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Instance not found".to_string())?;

    let global_settings = state
        .settings
        .get()
        .await
        .map_err(|e| e.to_string())?;

    settings.auto_install_java = global_settings.auto_install_java;
    if settings.java_path.is_none() {
        settings.java_path = global_settings.default_java_path.clone();
    }

    let (cancel_tx, cancel_rx) = watch::channel(false);
    {
        let mut guard = state.cancel_channels.lock().unwrap();
        guard.insert(instance_id.clone(), cancel_tx);
    }

    let launcher = Launcher::new(
        state.profiles_dir.clone(),
        state.caches_dir.clone(),
        state.resolver.clone(),
    );

    let (tx, mut rx) = mpsc::unbounded_channel::<LaunchEvent>();

    let emit_instance_id = instance_id.clone();
    tauri::async_runtime::spawn(async move {
        let _ = launcher
            .launch(
                &instance.folder_name,
                &version_id,
                &settings,
                &access_token,
                &profile.name,
                &profile.id,
                tx,
                cancel_rx,
            )
            .await;
    });

    while let Some(event) = rx.recv().await {
        match event {
            LaunchEvent::Progress { current, total, message } => {
                app.emit("launch://progress", serde_json::json!({
                    "instance_id": emit_instance_id,
                    "current": current,
                    "total": total,
                    "message": message
                }))
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Log { line } => {
                app.emit("launch://log", serde_json::json!({
                    "instance_id": emit_instance_id,
                    "line": line
                }))
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Started { pid } => {
                app.emit("launch://started", serde_json::json!({
                    "instance_id": emit_instance_id,
                    "pid": pid
                }))
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Exited { code } => {
                app.emit("launch://exited", serde_json::json!({
                    "instance_id": emit_instance_id,
                    "code": code
                }))
                .map_err(|e| e.to_string())?;
                break;
            }
        }
    }

    {
        let mut guard = state.cancel_channels.lock().unwrap();
        guard.remove(&instance_id);
    }

    Ok(())
}

#[tauri::command]
pub async fn cancel_launch(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<(), String> {
    let guard = state.cancel_channels.lock().unwrap();
    if let Some(tx) = guard.get(&instance_id) {
        let _ = tx.send(true);
    }
    Ok(())
}