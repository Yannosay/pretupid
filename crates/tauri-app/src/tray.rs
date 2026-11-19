use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{
    AppHandle, Manager, PhysicalPosition, Position, WebviewUrl, WebviewWindow,
    WebviewWindowBuilder, Wry,
};

pub const TRAY_ID: &str = "pretupid-main-tray";
pub const TRAY_PANEL_LABEL: &str = "tray-panel";

const PANEL_WIDTH: f64 = 360.0;
const PANEL_HEIGHT: f64 = 500.0;

pub const MAIN_WINDOW_LABEL: &str = "main";

pub fn attach_main_window_events(window: &WebviewWindow<Wry>) {
    let handle = window.clone();
    window.on_window_event(move |event| {
        if let tauri::WindowEvent::CloseRequested { api, .. } = event {
            api.prevent_close();
            let _ = handle.hide();
        }
    });
}

fn main_window(app: &AppHandle<Wry>) -> Option<WebviewWindow<Wry>> {
    if let Some(window) = app.get_webview_window(MAIN_WINDOW_LABEL) {
        return Some(window);
    }
    let config = app
        .config()
        .app
        .windows
        .iter()
        .find(|w| w.label == MAIN_WINDOW_LABEL)?
        .clone();
    match WebviewWindowBuilder::from_config(app, &config).and_then(|b| b.build()) {
        Ok(window) => {
            attach_main_window_events(&window);
            Some(window)
        }
        Err(err) => {
            tracing::warn!("failed to recreate main window: {err}");
            None
        }
    }
}

pub fn focus_main_window(app: &AppHandle<Wry>) {
    let app_for_main = app.clone();
    let shown = app.run_on_main_thread(move || {
        let Some(window) = main_window(&app_for_main) else {
            return;
        };
        let _ = window.unminimize();
        let _ = window.show();

        #[cfg(target_os = "windows")]
        {
            let _ = window.set_always_on_top(true);
        }

        let _ = window.set_focus();

        #[cfg(target_os = "windows")]
        {
            let window_handle = window.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(Duration::from_millis(120)).await;
                let _ = window_handle.set_always_on_top(false);
            });
        }
    });
    if let Err(err) = shown {
        tracing::warn!("failed to show main window: {err}");
    }
}

pub fn create_panel_window(app: &AppHandle<Wry>) -> tauri::Result<()> {
    if app.get_webview_window(TRAY_PANEL_LABEL).is_some() {
        return Ok(());
    }

    let panel =
        WebviewWindowBuilder::new(app, TRAY_PANEL_LABEL, WebviewUrl::App("index.html".into()))
            .title("Pretupid")
            .inner_size(PANEL_WIDTH, PANEL_HEIGHT)
            .resizable(false)
            .decorations(false)
            .always_on_top(true)
            .skip_taskbar(true)
            .visible(false)
            .focused(false)
            .shadow(true)
            .build()?;

    let was_focused = Arc::new(AtomicBool::new(false));
    let was_focused_clone = was_focused.clone();
    let panel_handle = panel.clone();

    panel.on_window_event(move |event| match event {
        tauri::WindowEvent::Focused(true) => {
            was_focused_clone.store(true, Ordering::SeqCst);
        }
        tauri::WindowEvent::Focused(false) => {
            let had_focus = was_focused_clone.swap(false, Ordering::SeqCst);
            if !had_focus {
                return;
            }
            let panel_for_task = panel_handle.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(Duration::from_millis(220)).await;
                if !panel_for_task.is_focused().unwrap_or(true) {
                    let _ = panel_for_task.hide();
                }
            });
        }
        _ => {}
    });

    Ok(())
}

fn toggle_panel(app: &AppHandle<Wry>, cursor: PhysicalPosition<f64>) -> tauri::Result<()> {
    let panel = match app.get_webview_window(TRAY_PANEL_LABEL) {
        Some(w) => w,
        None => return Ok(()),
    };

    if panel.is_visible().unwrap_or(false) {
        panel.hide()?;
        return Ok(());
    }

    let scale = panel.scale_factor().unwrap_or(1.0);
    let panel_w = PANEL_WIDTH * scale;
    let panel_h = PANEL_HEIGHT * scale;

    let mut x = cursor.x - panel_w - 12.0 * scale;
    let mut y = cursor.y - panel_h - 12.0 * scale;

    if let Ok(Some(monitor)) = panel.current_monitor() {
        let mpos = monitor.position();
        let msize = monitor.size();
        let min_x = mpos.x as f64;
        let min_y = mpos.y as f64;
        let max_x = min_x + msize.width as f64 - panel_w;
        let max_y = min_y + msize.height as f64 - panel_h;
        x = x.max(min_x).min(max_x);
        y = y.max(min_y).min(max_y);
    }

    panel.set_position(Position::Physical(PhysicalPosition::new(
        x as i32, y as i32,
    )))?;
    panel.show()?;
    panel.set_focus()?;
    Ok(())
}

pub fn build_tray(app: &AppHandle<Wry>) -> tauri::Result<()> {
    let mut builder = TrayIconBuilder::with_id(TRAY_ID)
        .tooltip("Pretupid Launcher")
        .show_menu_on_left_click(false)
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button,
                button_state: MouseButtonState::Up,
                position,
                ..
            } = event
            {
                let app = tray.app_handle();
                match button {
                    MouseButton::Left => focus_main_window(app),
                    MouseButton::Right => {
                        if let Err(err) = toggle_panel(app, position) {
                            tracing::warn!("failed to toggle tray panel: {err}");
                        }
                    }
                    _ => {}
                }
            }
        });

    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }

    builder.build(app)?;
    Ok(())
}
