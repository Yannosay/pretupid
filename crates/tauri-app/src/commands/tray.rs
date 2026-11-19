use tauri::{AppHandle, Emitter, Manager};

use crate::tray::TRAY_PANEL_LABEL;

#[tauri::command]
pub async fn hide_tray_panel(app: AppHandle) -> Result<(), String> {
    if let Some(panel) = app.get_webview_window(TRAY_PANEL_LABEL) {
        panel.hide().map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[tauri::command]
pub async fn open_main_window(app: AppHandle, route: Option<String>) -> Result<(), String> {
    crate::tray::focus_main_window(&app);

    if let Some(panel) = app.get_webview_window(TRAY_PANEL_LABEL) {
        let _ = panel.hide();
    }

    if let Some(route_value) = route {
        if !route_value.is_empty() {
            let _ = app.emit_to("main", "main://navigate", route_value);
        }
    }

    Ok(())
}

#[tauri::command]
pub async fn request_launch_from_tray(app: AppHandle, instance_id: String) -> Result<(), String> {
    crate::tray::focus_main_window(&app);

    if let Some(panel) = app.get_webview_window(TRAY_PANEL_LABEL) {
        let _ = panel.hide();
    }

    app.emit_to("main", "tray-panel://launch", instance_id)
        .map_err(|e| e.to_string())?;

    Ok(())
}

#[tauri::command]
pub async fn quit_app(app: AppHandle) -> Result<(), String> {
    app.exit(0);
    Ok(())
}
