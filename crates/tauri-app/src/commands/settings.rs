use pretupid_core::settings::GlobalSettings;
use tauri::State;

use crate::state::AppState;

#[tauri::command]
pub async fn get_global_settings(
    state: State<'_, AppState>,
) -> Result<GlobalSettings, String> {
    state
        .settings
        .get()
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn save_global_settings(
    state: State<'_, AppState>,
    settings: GlobalSettings,
) -> Result<(), String> {
    state
        .settings
        .save(&settings)
        .await
        .map_err(|e| e.to_string())
}