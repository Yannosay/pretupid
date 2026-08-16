use serde::{Deserialize, Serialize};
use sqlx::SqlitePool;

use crate::error::{CoreError, CoreResult};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GlobalSettings {
    pub default_java_path: Option<String>,
    pub auto_install_java: bool,
    #[serde(default)]
    pub show_snapshots: bool,
}

impl Default for GlobalSettings {
    fn default() -> Self {
        Self {
            default_java_path: None,
            auto_install_java: true,
            show_snapshots: false,
        }
    }
}

pub struct SettingsRepository {
    pool: SqlitePool,
}

impl SettingsRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn init_schema(&self) -> CoreResult<()> {
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS global_settings (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )",
        )
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn get(&self) -> CoreResult<GlobalSettings> {
        let row: Option<(String,)> = sqlx::query_as(
            "SELECT value FROM global_settings WHERE key = 'global'",
        )
        .fetch_optional(&self.pool)
        .await?;

        match row {
            Some((json,)) => serde_json::from_str(&json).map_err(CoreError::from),
            None => Ok(GlobalSettings::default()),
        }
    }

    pub async fn save(&self, settings: &GlobalSettings) -> CoreResult<()> {
        let json = serde_json::to_string(settings)?;
        sqlx::query(
            "INSERT OR REPLACE INTO global_settings (key, value) VALUES ('global', ?)",
        )
        .bind(&json)
        .execute(&self.pool)
        .await?;

        Ok(())
    }
}