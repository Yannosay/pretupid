use pretupid_core::settings::GlobalSettings;
use tauri::ipc::Response;
use tauri::State;

use crate::state::AppState;

#[tauri::command]
pub async fn get_global_settings(state: State<'_, AppState>) -> Result<GlobalSettings, String> {
    state.settings.get().await.map_err(|e| e.to_string())
}

const MAX_FONT_BYTES: u64 = 10 * 1024 * 1024;
const FONT_EXTENSIONS: [&str; 4] = ["ttf", "otf", "woff", "woff2"];

#[tauri::command]
pub async fn read_font_file(path: String) -> Result<Response, String> {
    let path = std::path::PathBuf::from(path);
    let extension = path
        .extension()
        .and_then(|value| value.to_str())
        .map(|value| value.to_ascii_lowercase())
        .unwrap_or_default();
    if !FONT_EXTENSIONS.contains(&extension.as_str()) {
        return Err("Choose a .ttf, .otf, .woff or .woff2 font file.".to_string());
    }
    let metadata = tokio::fs::metadata(&path)
        .await
        .map_err(|_| "The font file could not be found.".to_string())?;
    if !metadata.is_file() || metadata.len() > MAX_FONT_BYTES {
        return Err("Font files must be smaller than 10 MB.".to_string());
    }
    let bytes = tokio::fs::read(&path)
        .await
        .map_err(|e| format!("Could not read the font: {e}"))?;
    Ok(Response::new(bytes))
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
