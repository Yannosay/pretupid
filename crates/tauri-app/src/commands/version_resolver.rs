use mc_launch::loaders::{self, LoaderVersion};
use mc_launch::LoaderKind;

#[tauri::command]
pub async fn get_loader_versions(
    loader: String,
    mc_version: String,
) -> Result<Vec<LoaderVersion>, String> {
    let kind = LoaderKind::parse(&loader).ok_or_else(|| format!("Unknown loader '{loader}'"))?;
    if !mc_launch::VersionResolver::is_valid_version_id(&mc_version) {
        return Err("Invalid Minecraft version".to_string());
    }
    let client = mc_launch::download::http_client().map_err(|e| e.to_string())?;
    loaders::list_loader_versions(&client, kind, &mc_version)
        .await
        .map_err(|e| e.to_string())
}
