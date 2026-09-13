mod commands;
mod state;
mod tray;

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
        .plugin(tauri_plugin_updater::Builder::new().build())
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
                .expect("invalid database path")
                .to_string();

            let setup_meta = meta_dir.clone();
            let setup_profiles = profiles_dir.clone();
            let setup_caches = caches_dir.clone();

            let state = tauri::async_runtime::block_on(async move {
                AppState::new(
                    &database_path_str,
                    setup_meta,
                    setup_profiles,
                    setup_caches,
                )
                .await
                .expect("failed to initialize app state")
            });
            app.manage(state);

            let handle = app.handle().clone();

            if let Err(err) = tray::create_panel_window(&handle) {
                tracing::warn!("failed to create tray panel window: {err}");
            }

            if let Err(err) = tray::build_tray(&handle) {
                tracing::warn!("failed to build tray icon: {err}");
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::app_info::get_app_info,
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
            commands::instances::list_instance_mods,
            commands::instances::add_instance_mod,
            commands::instances::remove_instance_mod,
            commands::instances::create_instance_from_modpack,
            commands::profile::list_skin_slots,
            commands::profile::save_skin_slot,
            commands::profile::delete_skin_slot,
            commands::profile::set_active_skin_slot,
            commands::profile::import_skin_from_file,
            commands::profile::import_skin_from_url,
            commands::profile::apply_skin_slot,
            commands::profile::export_skin_slot,
            commands::profile::reorder_skin_slots,
            commands::profile::get_current_skin,
            commands::launch::fetch_versions,
            commands::launch::launch_instance,
            commands::launch::cancel_launch,
            commands::settings::get_global_settings,
            commands::settings::save_global_settings,
            commands::modrinth::search_modrinth,
            commands::modrinth::get_modrinth_project,
            commands::modrinth::get_modrinth_versions,
            commands::modrinth::get_modrinth_categories,
            commands::modrinth::get_modrinth_loaders,
            commands::modrinth::get_modrinth_game_versions,
            commands::tray::hide_tray_panel,
            commands::tray::open_main_window,
            commands::tray::request_launch_from_tray,
            commands::tray::quit_app,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Pretupid Launcher");
}

fn get_base_dir() -> std::path::PathBuf {
    if cfg!(target_os = "windows") {
        std::path::PathBuf::from(std::env::var("APPDATA").unwrap_or_else(|_| ".".to_string()))
            .join("PretupidLauncher")
    } else {
        std::path::PathBuf::from(
            std::env::var("XDG_DATA_HOME").unwrap_or_else(|_| {
                std::env::var("HOME")
                    .map(|h| format!("{}/.local/share", h))
                    .unwrap_or_else(|_| ".".to_string())
            }),
        )
        .join("pretupid-launcher")
    }
}