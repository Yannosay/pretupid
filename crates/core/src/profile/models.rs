use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SkinSlot {
    pub id: String,
    pub profile_id: String,
    pub slot_number: u32,
    pub name: String,
    pub file_name: String,
    pub original_file_name: Option<String>,
    pub model_type: SkinModelType,
    pub source: SkinSource,
    pub is_active: bool,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SkinModelType {
    Classic,
    Slim,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SkinSource {
    Mojang,
    LocalFile,
    Url,
}