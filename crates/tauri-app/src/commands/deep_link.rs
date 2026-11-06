use tauri::State;

use crate::state::AppState;

#[tauri::command]
pub async fn take_pending_deep_links(
    state: State<'_, AppState>,
) -> Result<Vec<serde_json::Value>, String> {
    let mut queue = state
        .pending_deep_links
        .lock()
        .map_err(|_| "deep-link queue lock poisoned".to_string())?;

    let items: Vec<serde_json::Value> = queue.drain(..).collect();
    Ok(items)
}
