use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{
    AppHandle, Manager, PhysicalPosition, Position, WebviewUrl, WebviewWindowBuilder, Wry,
};

pub const TRAY_ID: &str = "pretupid-main-tray";
pub const TRAY_PANEL_LABEL: &str = "tray-panel";

const PANEL_WIDTH: f64 = 340.0;
const PANEL_HEIGHT: f64 = 480.0;

fn focus_main_window(app: &AppHandle<Wry>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

pub fn create_panel_window(app: &AppHandle<Wry>) -> tauri::Result<()> {
    if app.get_webview_window(TRAY_PANEL_LABEL).is_some() {
        return Ok(());
    }

    let panel = WebviewWindowBuilder::new(
        app,
        TRAY_PANEL_LABEL,
        WebviewUrl::App("index.html".into()),
    )
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

    let panel_handle = panel.clone();
    panel.on_window_event(move |event| {
        if let tauri::WindowEvent::Focused(false) = event {
            let panel = panel_handle.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(std::time::Duration::from_millis(180)).await;
                if !panel.is_focused().unwrap_or(true) {
                    let _ = panel.hide();
                }
            });
        }
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

    panel.set_position(Position::Physical(PhysicalPosition::new(x as i32, y as i32)))?;
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