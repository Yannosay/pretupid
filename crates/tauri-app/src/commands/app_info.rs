use tauri::AppHandle;

#[derive(Debug, serde::Serialize)]
pub struct AppInfo {
    pub name: String,
    pub version: String,
}

#[tauri::command]
pub fn get_app_info(app: AppHandle) -> AppInfo {
    let package_info = app.package_info();

    let name = env!("CARGO_PKG_NAME");
    let workspace_name = if name == "pretupid" {
        "Pretupid Launcher".to_string()
    } else {
        package_info.name.clone()
    };

    AppInfo {
        name: workspace_name,
        version: env!("CARGO_PKG_VERSION").to_string(),
    }
}
