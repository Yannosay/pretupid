use base64::{engine::general_purpose::STANDARD, Engine as _};
use chrono::Utc;
use pretupid_core::auth::oauth::OAuthClient;
use pretupid_core::profile::{SkinModelType, SkinSlot, SkinSource};
use tauri::State;
use uuid::Uuid;

use crate::state::AppState;

const MAX_SKIN_BYTES: usize = 2 * 1024 * 1024;
const MINECRAFT_SKIN_URL: &str = "https://api.minecraftservices.com/minecraft/profile/skins";

#[derive(Debug, serde::Serialize)]
pub struct SkinSlotDto {
    pub id: String,
    pub profile_id: String,
    pub slot_number: u32,
    pub name: String,
    pub file_name: String,
    pub original_file_name: Option<String>,
    pub model_type: SkinModelType,
    pub source: SkinSource,
    pub is_active: bool,
    pub created_at: String,
    pub updated_at: String,
    pub preview_path: String,
    pub data_url: String,
}

fn skin_base_dir(state: &AppState, profile_id: &str) -> std::path::PathBuf {
    state.profiles_dir.join("skins").join(profile_id)
}

fn preview_path_for(state: &AppState, profile_id: &str, file_name: &str) -> String {
    skin_base_dir(state, profile_id)
        .join(file_name)
        .to_string_lossy()
        .replace("\\", "/")
}

fn dto_from_slot(state: &AppState, slot: SkinSlot) -> SkinSlotDto {
    let preview_path = preview_path_for(state, &slot.profile_id, &slot.file_name);
    let data_url = std::fs::read(&preview_path)
        .map(|bytes| format!("data:image/png;base64,{}", STANDARD.encode(&bytes)))
        .unwrap_or_default();

    SkinSlotDto {
        id: slot.id,
        profile_id: slot.profile_id,
        slot_number: slot.slot_number,
        name: slot.name,
        file_name: slot.file_name,
        original_file_name: slot.original_file_name,
        model_type: slot.model_type,
        source: slot.source,
        is_active: slot.is_active,
        created_at: slot.created_at.to_rfc3339(),
        updated_at: slot.updated_at.to_rfc3339(),
        preview_path,
        data_url,
    }
}

fn parse_model_type(value: &str) -> Result<SkinModelType, String> {
    match value {
        "classic" => Ok(SkinModelType::Classic),
        "slim" => Ok(SkinModelType::Slim),
        _ => Err(format!("Invalid skin model type: {value}")),
    }
}

fn validate_png_bytes(bytes: &[u8]) -> Result<(), String> {
    if bytes.len() > MAX_SKIN_BYTES {
        return Err("Skin file exceeds the 2 MB size limit.".to_string());
    }

    let png_signature = [137_u8, 80, 78, 71, 13, 10, 26, 10];
    if bytes.len() < png_signature.len() || bytes[..png_signature.len()] != png_signature {
        return Err("Skin file must be a valid PNG image.".to_string());
    }

    Ok(())
}

async fn write_skin_file(
    dir: &std::path::Path,
    file_name: &str,
    bytes: &[u8],
) -> Result<std::path::PathBuf, String> {
    tokio::fs::create_dir_all(dir)
        .await
        .map_err(|e| e.to_string())?;

    let final_path = dir.join(file_name);
    let temp_path = dir.join(format!("{}.tmp", file_name));

    tokio::fs::write(&temp_path, bytes)
        .await
        .map_err(|e| e.to_string())?;

    tokio::fs::rename(&temp_path, &final_path)
        .await
        .map_err(|e| e.to_string())?;

    Ok(final_path)
}

#[tauri::command]
pub async fn list_skin_slots(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<Vec<SkinSlotDto>, String> {
    let slots = state
        .skins
        .list_slots(&profile_id)
        .await
        .map_err(|e| e.to_string())?;

    Ok(slots
        .into_iter()
        .map(|slot| dto_from_slot(&state, slot))
        .collect())
}

#[tauri::command]
pub async fn save_skin_slot(
    state: State<'_, AppState>,
    slot_id: String,
    name: String,
    model_type: String,
    source: String,
    file_name: Option<String>,
    original_file_name: Option<String>,
) -> Result<SkinSlotDto, String> {
    let mut slot = state
        .skins
        .get_slot_by_id(&slot_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Skin slot not found".to_string())?;

    slot.name = name;
    slot.model_type = parse_model_type(&model_type)?;
    slot.source = match source.as_str() {
        "mojang" => SkinSource::Mojang,
        "url" => SkinSource::Url,
        _ => SkinSource::LocalFile,
    };

    if let Some(value) = file_name {
        slot.file_name = value;
    }

    if original_file_name.is_some() {
        slot.original_file_name = original_file_name;
    }

    slot.updated_at = Utc::now();

    state.skins.update_slot(&slot).await.map_err(|e| e.to_string())?;
    Ok(dto_from_slot(&state, slot))
}

#[tauri::command]
pub async fn delete_skin_slot(
    state: State<'_, AppState>,
    slot_id: String,
) -> Result<bool, String> {
    let slot = state
        .skins
        .get_slot_by_id(&slot_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Skin slot not found".to_string())?;

    let profile_id = slot.profile_id.clone();
    let file_path = skin_base_dir(&state, &profile_id).join(&slot.file_name);

    state
        .skins
        .delete_slot(&slot_id)
        .await
        .map_err(|e| e.to_string())?;

    if file_path.exists() {
        let _ = tokio::fs::remove_file(&file_path).await;
    }

    let remaining = state
        .skins
        .list_slots(&profile_id)
        .await
        .map_err(|e| e.to_string())?;

    if remaining.is_empty() {
        return Ok(true);
    }

    let ordered_ids = remaining
        .iter()
        .map(|slot| slot.id.clone())
        .collect::<Vec<_>>();

    state
        .skins
        .reorder_slots(&profile_id, &ordered_ids)
        .await
        .map_err(|e| e.to_string())?;

    if !remaining.iter().any(|slot| slot.is_active) {
        state
            .skins
            .set_active(&profile_id, &remaining[0].id)
            .await
            .map_err(|e| e.to_string())?;
    }

    Ok(true)
}

#[tauri::command]
pub async fn set_active_skin_slot(
    state: State<'_, AppState>,
    profile_id: String,
    slot_id: String,
) -> Result<(), String> {
    state
        .skins
        .set_active(&profile_id, &slot_id)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn import_skin_from_file(
    state: State<'_, AppState>,
    profile_id: String,
    slot_number: u32,
    name: String,
    model_type: String,
    original_file_name: Option<String>,
    file_data_base64: String,
) -> Result<SkinSlotDto, String> {
    let existing = state
        .skins
        .list_slots(&profile_id)
        .await
        .map_err(|e| e.to_string())?;

    if existing.len() >= 5 {
        return Err("Skin slot limit reached. Remove a skin before adding another.".to_string());
    }

    if existing.iter().any(|slot| slot.slot_number == slot_number) {
        return Err("A skin already uses that slot number.".to_string());
    }

    let bytes = STANDARD
        .decode(file_data_base64.as_bytes())
        .map_err(|e| format!("Invalid skin file data: {e}"))?;

    validate_png_bytes(&bytes)?;

    let slot_id = Uuid::new_v4().to_string();
    let file_name = format!("{slot_id}.png");
    let dir = skin_base_dir(&state, &profile_id);
    write_skin_file(&dir, &file_name, &bytes).await?;

    let now = Utc::now();
    let slot = SkinSlot {
        id: slot_id,
        profile_id: profile_id.clone(),
        slot_number,
        name,
        file_name: file_name.clone(),
        original_file_name,
        model_type: parse_model_type(&model_type)?,
        source: SkinSource::LocalFile,
        is_active: existing.is_empty(),
        created_at: now,
        updated_at: now,
    };

    if let Err(e) = state.skins.create_slot(&slot).await {
        let _ = tokio::fs::remove_file(dir.join(&file_name)).await;
        return Err(e.to_string());
    }

    Ok(dto_from_slot(&state, slot))
}

#[tauri::command]
pub async fn import_skin_from_url(
    state: State<'_, AppState>,
    profile_id: String,
    slot_number: u32,
    name: String,
    model_type: String,
    url: String,
) -> Result<SkinSlotDto, String> {
    let existing = state
        .skins
        .list_slots(&profile_id)
        .await
        .map_err(|e| e.to_string())?;

    if existing.len() >= 5 {
        return Err("Skin slot limit reached. Remove a skin before adding another.".to_string());
    }

    if existing.iter().any(|slot| slot.slot_number == slot_number) {
        return Err("A skin already uses that slot number.".to_string());
    }

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(60))
        .build()
        .map_err(|e| e.to_string())?;

    let response = client
        .get(&url)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !response.status().is_success() {
        return Err(format!("Skin download failed: {}", response.status()));
    }

    let bytes = response
        .bytes()
        .await
        .map_err(|e| e.to_string())?
        .to_vec();

    validate_png_bytes(&bytes)?;

    let slot_id = Uuid::new_v4().to_string();
    let file_name = format!("{slot_id}.png");
    let dir = skin_base_dir(&state, &profile_id);
    write_skin_file(&dir, &file_name, &bytes).await?;

    let now = Utc::now();
    let slot = SkinSlot {
        id: slot_id,
        profile_id: profile_id.clone(),
        slot_number,
        name,
        file_name: file_name.clone(),
        original_file_name: None,
        model_type: parse_model_type(&model_type)?,
        source: SkinSource::Url,
        is_active: existing.is_empty(),
        created_at: now,
        updated_at: now,
    };

    if let Err(e) = state.skins.create_slot(&slot).await {
        let _ = tokio::fs::remove_file(dir.join(&file_name)).await;
        return Err(e.to_string());
    }

    Ok(dto_from_slot(&state, slot))
}

#[tauri::command]
pub async fn apply_skin_slot(
    state: State<'_, AppState>,
    slot_id: String,
) -> Result<(), String> {
    let slot = state
        .skins
        .get_slot_by_id(&slot_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Skin slot not found".to_string())?;

    let oauth = OAuthClient::new(state.pool.clone());
    let tokens = oauth
        .get_tokens()
        .await
        .map_err(|e| e.to_string())?;

    let file_path = skin_base_dir(&state, &slot.profile_id).join(&slot.file_name);
    let bytes = tokio::fs::read(&file_path)
        .await
        .map_err(|e| e.to_string())?;

    let variant = match slot.model_type {
        SkinModelType::Classic => "classic",
        SkinModelType::Slim => "slim",
    };

    let part = reqwest::multipart::Part::bytes(bytes)
        .file_name(slot.file_name.clone())
        .mime_str("image/png")
        .map_err(|e| e.to_string())?;

    let form = reqwest::multipart::Form::new()
        .text("variant", variant)
        .part("file", part);

    let response = reqwest::Client::new()
        .post(MINECRAFT_SKIN_URL)
        .bearer_auth(tokens.access_token)
        .multipart(form)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !response.status().is_success() {
        return Err(format!(
            "Minecraft skin upload failed with status {}",
            response.status()
        ));
    }

    Ok(())
}

#[tauri::command]
pub async fn export_skin_slot(
    state: State<'_, AppState>,
    slot_id: String,
) -> Result<String, String> {
    let slot = state
        .skins
        .get_slot_by_id(&slot_id)
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Skin slot not found".to_string())?;

    let file_path = skin_base_dir(&state, &slot.profile_id).join(&slot.file_name);
    let bytes = tokio::fs::read(&file_path)
        .await
        .map_err(|e| e.to_string())?;

    Ok(STANDARD.encode(bytes))
}

#[tauri::command]
pub async fn reorder_skin_slots(
    state: State<'_, AppState>,
    profile_id: String,
    ordered_slot_ids: Vec<String>,
) -> Result<(), String> {
    let current = state
        .skins
        .list_slots(&profile_id)
        .await
        .map_err(|e| e.to_string())?;

    if ordered_slot_ids.len() != current.len() {
        return Err("Reordered slot list does not match the current slots.".to_string());
    }

    let current_ids = current
        .iter()
        .map(|slot| slot.id.clone())
        .collect::<std::collections::HashSet<_>>();

    if !ordered_slot_ids.iter().all(|id| current_ids.contains(id)) {
        return Err("Reordered slot list contains invalid slot IDs.".to_string());
    }

    state
        .skins
        .reorder_slots(&profile_id, &ordered_slot_ids)
        .await
        .map_err(|e| e.to_string())
}

#[derive(Debug, serde::Serialize)]
pub struct CurrentSkinDto {
    pub preview_path: String,
    pub model_type: SkinModelType,
    pub data_url: String,
}

#[tauri::command]
pub async fn get_current_skin(
    state: State<'_, AppState>,
    profile_id: String,
) -> Result<CurrentSkinDto, String> {
    let oauth = OAuthClient::new(state.pool.clone());
    let tokens = oauth
        .get_tokens()
        .await
        .map_err(|e| e.to_string())?;

    let response = reqwest::Client::new()
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(tokens.access_token)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !response.status().is_success() {
        return Err(format!(
            "Minecraft profile fetch failed with status {}",
            response.status()
        ));
    }

    let json: serde_json::Value = response
        .json()
        .await
        .map_err(|e| e.to_string())?;

    let skins = json
        .get("skins")
        .and_then(|value| value.as_array())
        .ok_or_else(|| "Minecraft profile response has no skins array.".to_string())?;

    let active_skin = skins
        .iter()
        .find(|skin| {
            skin.get("state")
                .and_then(|value| value.as_str())
                == Some("ACTIVE")
        })
        .or_else(|| skins.first())
        .ok_or_else(|| "Minecraft profile has no usable skin entry.".to_string())?;

    let skin_url = active_skin
        .get("url")
        .and_then(|value| value.as_str())
        .ok_or_else(|| "Minecraft profile skin entry has no URL.".to_string())?;

    let model_type = if active_skin
        .get("variant")
        .and_then(|value| value.as_str())
        == Some("slim")
    {
        SkinModelType::Slim
    } else {
        SkinModelType::Classic
    };

    let bytes = reqwest::get(skin_url)
        .await
        .map_err(|e| e.to_string())?
        .bytes()
        .await
        .map_err(|e| e.to_string())?;

    let file_name = "current_skin.png".to_string();
    let dir = skin_base_dir(&state, &profile_id);
    write_skin_file(&dir, &file_name, &bytes).await?;

    let data_url = format!("data:image/png;base64,{}", STANDARD.encode(&bytes));

    Ok(CurrentSkinDto {
        preview_path: preview_path_for(&state, &profile_id, &file_name),
        model_type,
        data_url,
    })
}