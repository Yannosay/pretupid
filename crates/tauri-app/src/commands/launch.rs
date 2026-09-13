use mc_launch::launcher::{LaunchEvent, Launcher};
use mc_launch::models::LaunchSettings;
use pretupid_core::auth::oauth::OAuthClient;
use pretupid_core::instance::ModLoader;
use tauri::{AppHandle, Emitter, State};
use tokio::sync::{mpsc, watch};

use crate::state::AppState;

#[tauri::command]
pub async fn fetch_versions() -> Result<Vec<mc_launch::models::VersionEntry>, String> {
    let client = reqwest::Client::new();
    let response = client
        .get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !response.status().is_success() {
        return Err(format!(
            "version manifest request failed: {}",
            response.status()
        ));
    }

    let json: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;

    let versions = json
        .get("versions")
        .and_then(|v| v.as_array())
        .ok_or_else(|| "missing versions array in manifest".to_string())?;

    Ok(versions
        .iter()
        .filter_map(|v| {
            Some(mc_launch::models::VersionEntry {
                id: v.get("id")?.as_str()?.to_string(),
                r#type: v.get("type")?.as_str()?.to_string(),
                url: v.get("url")?.as_str()?.to_string(),
                time: v.get("time").and_then(|t| t.as_str()).unwrap_or("").to_string(),
                release_time: v
                    .get("releaseTime")
                    .and_then(|t| t.as_str())
                    .unwrap_or("")
                    .to_string(),
                sha1: v
                    .get("sha1")
                    .and_then(|t| t.as_str())
                    .unwrap_or("")
                    .to_string(),
            })
        })
        .collect())
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
        .ensure_valid_token()
        .await
        .map_err(|e| e.to_string())?;

    let access_token = tokens.access_token.clone();
    if access_token.is_empty() {
        return Err("Stored token is empty. Please logout and login again.".to_string());
    }

    let auth_status = oauth.get_status().await.map_err(|e| e.to_string())?;

    let profile = auth_status
        .profile
        .ok_or_else(|| "Minecraft profile not found".to_string())?;

    let instance = state
        .instances
        .get_by_id(uuid::Uuid::parse_str(&instance_id).map_err(|e| e.to_string())?)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Instance not found".to_string())?;

    let global_settings = state.settings.get().await.map_err(|e| e.to_string())?;

    settings.auto_install_java = global_settings.auto_install_java;
    if settings.java_path.is_none() {
        settings.java_path = global_settings.default_java_path.clone();
    }

    let (cancel_tx, cancel_rx) = watch::channel(false);
    {
        let mut guard = state.cancel_channels.lock().unwrap();
        guard.insert(instance_id.clone(), cancel_tx);
    }

    let mods = state
        .instances
        .list_mods(&instance_id)
        .await
        .map_err(|e| e.to_string())?;

    let mods_dir = state.profiles_dir.join(&instance.folder_name).join("mods");
    std::fs::create_dir_all(&mods_dir).map_err(|e| e.to_string())?;

    for m in mods {
        if let (Some(url), Some(file_name)) = (m.file_url.clone(), m.file_name.clone()) {
            let path = mods_dir.join(&file_name);
            if !path.exists() {
                let client = reqwest::Client::builder()
                    .timeout(std::time::Duration::from_secs(300))
                    .build()
                    .map_err(|e| e.to_string())?;
                let response = client.get(&url).send().await.map_err(|e| e.to_string())?;
                if !response.status().is_success() {
                    return Err(format!("mod download failed: {}", response.status()));
                }
                let bytes = response.bytes().await.map_err(|e| e.to_string())?;
                tokio::fs::write(&path, &bytes)
                    .await
                    .map_err(|e| e.to_string())?;
            }
        }
    }

    let launcher = Launcher::new(
        state.profiles_dir.clone(),
        state.caches_dir.clone(),
        state.resolver.clone(),
    );

    let (tx, mut rx) = mpsc::unbounded_channel::<LaunchEvent>();

    let emit_instance_id = instance_id.clone();
    let mod_loader = match instance.mod_loader {
        ModLoader::Vanilla => "vanilla",
        ModLoader::Forge => "forge",
        ModLoader::Fabric => "fabric",
        ModLoader::Quilt => "quilt",
        ModLoader::NeoForge => "neoforge",
        ModLoader::Modrinth => "modrinth",
    };
    let loader_version = instance.loader_version.clone();

    tauri::async_runtime::spawn(async move {
        let _ = launcher
            .launch(
                &instance.folder_name,
                &version_id,
                &settings,
                &access_token,
                &profile.name,
                &profile.id,
                mod_loader,
                loader_version.as_deref(),
                tx,
                cancel_rx,
            )
            .await;
    });

    while let Some(event) = rx.recv().await {
        match event {
            LaunchEvent::Progress {
                current,
                total,
                message,
            } => {
                app.emit(
                    "launch://progress",
                    serde_json::json!({
                        "instance_id": emit_instance_id,
                        "current": current,
                        "total": total,
                        "message": message
                    }),
                )
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Log { line } => {
                app.emit(
                    "launch://log",
                    serde_json::json!({
                        "instance_id": emit_instance_id,
                        "line": line
                    }),
                )
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Started { pid } => {
                app.emit(
                    "launch://started",
                    serde_json::json!({
                        "instance_id": emit_instance_id,
                        "pid": pid
                    }),
                )
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Failed { message } => {
                app.emit(
                    "launch://failed",
                    serde_json::json!({
                        "instance_id": emit_instance_id,
                        "message": message
                    }),
                )
                .map_err(|e| e.to_string())?;
            }
            LaunchEvent::Exited { code } => {
                app.emit(
                    "launch://exited",
                    serde_json::json!({
                        "instance_id": emit_instance_id,
                        "code": code
                    }),
                )
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