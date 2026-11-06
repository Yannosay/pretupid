use pretupid_core::auth::oauth::OAuthClient;
use tauri::{AppHandle, Emitter, Manager, State, WebviewUrl, WebviewWindowBuilder};
use url::Url;

use crate::state::AppState;

#[tauri::command]
pub async fn login_microsoft(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let pool = state.pool.clone();
    let oauth = OAuthClient::new(pool);

    let auth_url = oauth.prepare_login().await.map_err(|e| e.to_string())?;

    let parsed_url = Url::parse(&auth_url).map_err(|e| e.to_string())?;

    let app_for_navigation = app.clone();

    WebviewWindowBuilder::new(&app, "oauth", WebviewUrl::External(parsed_url))
        .title("Microsoft Login")
        .inner_size(480.0, 640.0)
        .resizable(false)
        .on_navigation(move |url| {
            if url
                .as_str()
                .starts_with("https://login.live.com/oauth20_desktop.srf")
            {
                let parsed = match Url::parse(url.as_str()) {
                    Ok(p) => p,
                    Err(e) => {
                        tracing::error!("Failed to parse redirect URL: {}", e);
                        return false;
                    }
                };

                let code = match parsed
                    .query_pairs()
                    .find(|(k, _)| k == "code")
                    .map(|(_, v)| v.to_string())
                {
                    Some(c) => c,
                    None => {
                        tracing::error!("No code in redirect");
                        return false;
                    }
                };

                let state = match parsed
                    .query_pairs()
                    .find(|(k, _)| k == "state")
                    .map(|(_, v)| v.to_string())
                {
                    Some(s) => s,
                    None => {
                        tracing::error!("No state in redirect");
                        return false;
                    }
                };

                let pool = app_for_navigation.state::<AppState>().pool.clone();
                let oauth = OAuthClient::new(pool);
                let app = app_for_navigation.clone();
                tauri::async_runtime::spawn(async move {
                    match oauth.handle_callback(&code, &state).await {
                        Ok(_) => {
                            let _ = app.emit("auth-state", ());
                            if let Some(window) = app.get_webview_window("oauth") {
                                let _ = window.close();
                            }
                        }
                        Err(e) => {
                            tracing::error!("OAuth callback failed: {}", e);
                        }
                    }
                });

                false
            } else {
                true
            }
        })
        .build()
        .map_err(|e| e.to_string())?;

    Ok(())
}

#[tauri::command]
pub async fn logout_microsoft(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let oauth = OAuthClient::new(state.pool.clone());
    oauth.logout().await.map_err(|e| e.to_string())?;
    app.emit("auth-state", ()).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub async fn get_auth_status(
    state: State<'_, AppState>,
) -> Result<pretupid_core::auth::models::AuthStatus, String> {
    let oauth = OAuthClient::new(state.pool.clone());
    oauth.get_status().await.map_err(|e| e.to_string())
}
