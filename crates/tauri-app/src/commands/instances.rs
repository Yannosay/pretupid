use pretupid_core::instance::{
    CreateInstanceRequest, InstanceSettings, UpdateInstanceRequest,
};
use tauri::State;
use uuid::Uuid;

use crate::state::AppState;

#[tauri::command]
pub async fn create_instance(
    state: State<'_, AppState>,
    request: CreateInstanceRequest,
) -> Result<pretupid_core::instance::Instance, String> {
    let instance = state
        .instances
        .create(request)
        .await
        .map_err(|e| e.to_string())?;

    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    std::fs::create_dir_all(&profile_dir).map_err(|e| e.to_string())?;

    Ok(instance)
}

#[tauri::command]
pub async fn list_instances(
    state: State<'_, AppState>,
) -> Result<Vec<pretupid_core::instance::Instance>, String> {
    state
        .instances
        .list()
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_instance(
    state: State<'_, AppState>,
    id: String,
) -> Result<Option<pretupid_core::instance::Instance>, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .get_by_id(uuid)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn update_instance(
    state: State<'_, AppState>,
    id: String,
    request: UpdateInstanceRequest,
) -> Result<Option<pretupid_core::instance::Instance>, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .update(uuid, request)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn delete_instance(
    state: State<'_, AppState>,
    id: String,
) -> Result<bool, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    let instance = state
        .instances
        .get_by_id(uuid)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Instance not found".to_string())?;

    state
        .instances
        .delete(uuid)
        .await
        .map_err(|e| e.to_string())?;

    let profile_dir = state.profiles_dir.join(&instance.folder_name);
    if profile_dir.exists() {
        std::fs::remove_dir_all(&profile_dir).map_err(|e| e.to_string())?;
    }

    Ok(true)
}

#[tauri::command]
pub async fn save_instance_settings(
    state: State<'_, AppState>,
    id: String,
    settings: InstanceSettings,
) -> Result<(), String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .save_settings(uuid, &settings)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_instance_settings(
    state: State<'_, AppState>,
    id: String,
) -> Result<InstanceSettings, String> {
    let uuid = Uuid::parse_str(&id).map_err(|e| e.to_string())?;
    state
        .instances
        .get_settings(uuid)
        .await
        .map_err(|e| e.to_string())
}