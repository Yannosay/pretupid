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
    if let Some(window) = app.get_webview_window("main") {
        window.show().map_err(|e| e.to_string())?;
        window.unminimize().map_err(|e| e.to_string())?;

        #[cfg(target_os = "windows")]
        {
            let _ = window.set_always_on_top(true);
        }

        window.set_focus().map_err(|e| e.to_string())?;

        #[cfg(target_os = "windows")]
        {
            let window_handle = window.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(std::time::Duration::from_millis(120)).await;
                let _ = window_handle.set_always_on_top(false);
            });
        }
    }

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
    if let Some(window) = app.get_webview_window("main") {
        window.show().map_err(|e| e.to_string())?;
        window.unminimize().map_err(|e| e.to_string())?;

        #[cfg(target_os = "windows")]
        {
            let _ = window.set_always_on_top(true);
        }

        window.set_focus().map_err(|e| e.to_string())?;

        #[cfg(target_os = "windows")]
        {
            let window_handle = window.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(std::time::Duration::from_millis(120)).await;
                let _ = window_handle.set_always_on_top(false);
            });
        }
    }

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
