mod commands;
mod state;

use state::AppState;
use tauri::Manager;
use tracing_subscriber::prelude::*;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let base_dir = get_base_dir();
    let logs_dir = base_dir.join("launcher_logs");
    std::fs::create_dir_all(&logs_dir).expect("failed to create logs directory");

    let file_appender = tracing_appender::rolling::daily(logs_dir.clone(), "pretupid-launcher.log");
    let (non_blocking, guard) = tracing_appender::non_blocking(file_appender);
    std::mem::forget(guard);

    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .with(tracing_subscriber::fmt::layer().with_writer(std::io::stdout))
        .with(tracing_subscriber::fmt::layer().with_writer(non_blocking))
        .init();

    tauri::Builder::default()
        .setup(move |app| {
            let meta_dir = base_dir.join("meta");
            let profiles_dir = base_dir.join("profiles");
            let caches_dir = base_dir.join("caches");

            for dir in [&logs_dir, &meta_dir, &profiles_dir, &caches_dir] {
                std::fs::create_dir_all(dir).expect("failed to create app directories");
            }

            let database_path = meta_dir.join("pretupid.db");
            let database_path_str = database_path
                .to_str()
                .expect("invalid database path");

            tauri::async_runtime::block_on(async {
                let state = AppState::new(
                    database_path_str,
                    meta_dir.clone(),
                    profiles_dir.clone(),
                    caches_dir.clone(),
                )
                .await
                .expect("failed to initialize app state");
                app.manage(state);
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::auth::login_microsoft,
            commands::auth::logout_microsoft,
            commands::auth::get_auth_status,
            commands::instances::create_instance,
            commands::instances::list_instances,
            commands::instances::get_instance,
            commands::instances::update_instance,
            commands::instances::delete_instance,
            commands::instances::save_instance_settings,
            commands::instances::get_instance_settings,
            commands::launch::fetch_versions,
            commands::launch::launch_instance,
            commands::launch::cancel_launch,
            commands::settings::get_global_settings,
            commands::settings::save_global_settings,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Pretupid Launcher");
}

fn get_base_dir() -> std::path::PathBuf {
    if cfg!(target_os = "windows") {
        std::path::PathBuf::from(
            std::env::var("APPDATA").unwrap_or_else(|_| ".".to_string())
        )
        .join("PretupidLauncher")
    } else {
        std::path::PathBuf::from(
            std::env::var("XDG_DATA_HOME").unwrap_or_else(|_| {
                std::env::var("HOME")
                    .map(|h| format!("{}/.local/share", h))
                    .unwrap_or_else(|_| ".".to_string())
            })
        )
        .join("pretupid-launcher")
    }
}