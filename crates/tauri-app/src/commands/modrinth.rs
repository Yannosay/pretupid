use crate::state::AppState;
use modrinth_client::models::{GameVersion, Loader, Category, Project, SearchResponse, Version};
use tauri::State;

#[tauri::command]
pub async fn search_modrinth(
    state: State<'_, AppState>,
    query: String,
    facets: Option<String>,
    index: Option<String>,
    offset: Option<u32>,
    limit: Option<u32>,
) -> Result<SearchResponse, String> {
    state.modrinth.search_projects(&query, facets.as_deref(), index.as_deref(), offset, limit)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_modrinth_project(
    state: State<'_, AppState>,
    id_or_slug: String,
) -> Result<Project, String> {
    state.modrinth.get_project(&id_or_slug)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_modrinth_versions(
    state: State<'_, AppState>,
    id_or_slug: String,
) -> Result<Vec<Version>, String> {
    state.modrinth.list_versions(&id_or_slug)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_modrinth_categories(
    state: State<'_, AppState>,
) -> Result<Vec<Category>, String> {
    state.modrinth.get_categories()
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_modrinth_loaders(
    state: State<'_, AppState>,
) -> Result<Vec<Loader>, String> {
    state.modrinth.get_loaders()
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn get_modrinth_game_versions(
    state: State<'_, AppState>,
) -> Result<Vec<GameVersion>, String> {
    state.modrinth.get_game_versions()
        .await
        .map_err(|e| e.to_string())
}