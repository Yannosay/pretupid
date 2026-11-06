use tauri::{AppHandle, Emitter, Manager};
use url::Url;

use crate::state::AppState;

pub fn focus_main_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();

        #[cfg(target_os = "windows")]
        {
            let _ = window.set_always_on_top(true);
        }

        let _ = window.set_focus();

        #[cfg(target_os = "windows")]
        {
            let handle = window.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(std::time::Duration::from_millis(120)).await;
                let _ = handle.set_always_on_top(false);
            });
        }
    }
}

pub fn handle_url(app: &AppHandle, raw: &str) {
    tracing::info!("deep-link received: {}", raw);

    if raw.len() > 2048 {
        tracing::warn!("ignoring oversized deep-link");
        return;
    }

    let parsed = match Url::parse(raw) {
        Ok(u) if u.scheme() == "pretupid" => u,
        Ok(_) => {
            tracing::warn!("ignoring deep-link with unexpected scheme");
            return;
        }
        Err(err) => {
            tracing::warn!("invalid deep-link URL: {raw} ({err})");
            return;
        }
    };

    let host = parsed.host_str().unwrap_or("").to_string();

    match host.as_str() {
        "open" => {
            focus_main_window(app);
        }
        "settings" => {
            focus_main_window(app);

            let mut theme: Option<String> = None;
            let mut accent: Option<String> = None;
            let mut edge: Option<String> = None;

            for (key, value) in parsed.query_pairs() {
                if value.len() > 32 {
                    continue;
                }
                let safe = value
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '#');
                if !safe {
                    continue;
                }
                match key.as_ref() {
                    "theme" => theme = Some(value.into_owned()),
                    "accent" => accent = Some(value.into_owned()),
                    "edge" => edge = Some(value.into_owned()),
                    _ => {}
                }
            }

            let payload = serde_json::json!({
                "type": "settings",
                "theme": theme,
                "accent": accent,
                "edge": edge
            });

            if let Some(state) = app.try_state::<AppState>() {
                if let Ok(mut queue) = state.pending_deep_links.lock() {
                    if queue.len() >= 8 {
                        queue.remove(0);
                    }
                    queue.push(payload.clone());
                }
            }

            if let Err(err) = app.emit("deep-link://action", &payload) {
                tracing::warn!("failed to emit deep-link action: {err}");
            } else {
                tracing::info!("deep-link settings action dispatched");
            }
        }
        _ => {
            tracing::info!("unhandled deep-link host: {host}");
        }
    }
}
