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

const EXTERNAL_HOSTS: [&str; 3] = ["pretupid.yannosay.com", "github.com", "modrinth.com"];

#[tauri::command]
pub fn open_external_link(url: String) -> Result<(), String> {
    let parsed = url::Url::parse(&url).map_err(|_| "Invalid link.".to_string())?;
    let host = parsed.host_str().unwrap_or_default();
    if parsed.scheme() != "https" || !EXTERNAL_HOSTS.contains(&host) {
        return Err("This link cannot be opened.".to_string());
    }
    let program = if cfg!(target_os = "windows") {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(program)
        .arg(parsed.as_str())
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("Could not open the link: {e}"))
}
