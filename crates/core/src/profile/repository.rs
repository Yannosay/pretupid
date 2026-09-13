use chrono::{DateTime, Utc};
use sqlx::SqlitePool;

use crate::error::CoreResult;
use super::models::{SkinModelType, SkinSlot, SkinSource};

type SkinRow = (
    String,
    String,
    i64,
    String,
    String,
    Option<String>,
    String,
    String,
    i64,
    String,
    String,
);

pub struct SkinRepository {
    pool: SqlitePool,
}

fn parse_dt(value: &str) -> DateTime<Utc> {
    DateTime::parse_from_rfc3339(value)
        .map(|dt| dt.with_timezone(&Utc))
        .unwrap_or_else(|_| Utc::now())
}

fn model_to_str(value: SkinModelType) -> &'static str {
    match value {
        SkinModelType::Classic => "classic",
        SkinModelType::Slim => "slim",
    }
}

fn str_to_model(value: &str) -> SkinModelType {
    match value {
        "slim" => SkinModelType::Slim,
        _ => SkinModelType::Classic,
    }
}

fn source_to_str(value: SkinSource) -> &'static str {
    match value {
        SkinSource::Mojang => "mojang",
        SkinSource::LocalFile => "local_file",
        SkinSource::Url => "url",
    }
}

fn str_to_source(value: &str) -> SkinSource {
    match value {
        "mojang" => SkinSource::Mojang,
        "url" => SkinSource::Url,
        _ => SkinSource::LocalFile,
    }
}

fn map_skin_row(row: SkinRow) -> SkinSlot {
    SkinSlot {
        id: row.0,
        profile_id: row.1,
        slot_number: row.2 as u32,
        name: row.3,
        file_name: row.4,
        original_file_name: row.5,
        model_type: str_to_model(&row.6),
        source: str_to_source(&row.7),
        is_active: row.8 != 0,
        created_at: parse_dt(&row.9),
        updated_at: parse_dt(&row.10),
    }
}

impl SkinRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn init_schema(&self) -> CoreResult<()> {
        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS skin_slots (
                id TEXT PRIMARY KEY NOT NULL,
                profile_id TEXT NOT NULL,
                slot_number INTEGER NOT NULL,
                name TEXT NOT NULL,
                file_name TEXT NOT NULL,
                original_file_name TEXT,
                model_type TEXT NOT NULL DEFAULT 'classic',
                source TEXT NOT NULL DEFAULT 'local_file',
                is_active INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                UNIQUE(profile_id, slot_number)
            );
            "#,
        )
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn list_slots(&self, profile_id: &str) -> CoreResult<Vec<SkinSlot>> {
        let rows: Vec<SkinRow> = sqlx::query_as(
            r#"
            SELECT id, profile_id, slot_number, name, file_name, original_file_name,
                   model_type, source, is_active, created_at, updated_at
            FROM skin_slots
            WHERE profile_id = ?
            ORDER BY slot_number ASC
            "#,
        )
        .bind(profile_id)
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(map_skin_row).collect())
    }

    pub async fn get_slot_by_id(&self, slot_id: &str) -> CoreResult<Option<SkinSlot>> {
        let row: Option<SkinRow> = sqlx::query_as(
            r#"
            SELECT id, profile_id, slot_number, name, file_name, original_file_name,
                   model_type, source, is_active, created_at, updated_at
            FROM skin_slots
            WHERE id = ?
            "#,
        )
        .bind(slot_id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(map_skin_row))
    }

    pub async fn get_slot(&self, profile_id: &str, slot_number: u32) -> CoreResult<Option<SkinSlot>> {
        let row: Option<SkinRow> = sqlx::query_as(
            r#"
            SELECT id, profile_id, slot_number, name, file_name, original_file_name,
                   model_type, source, is_active, created_at, updated_at
            FROM skin_slots
            WHERE profile_id = ? AND slot_number = ?
            "#,
        )
        .bind(profile_id)
        .bind(slot_number as i64)
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(map_skin_row))
    }

    pub async fn create_slot(&self, slot: &SkinSlot) -> CoreResult<SkinSlot> {
        sqlx::query(
            r#"
            INSERT INTO skin_slots
            (id, profile_id, slot_number, name, file_name, original_file_name,
             model_type, source, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(&slot.id)
        .bind(&slot.profile_id)
        .bind(slot.slot_number as i64)
        .bind(&slot.name)
        .bind(&slot.file_name)
        .bind(&slot.original_file_name)
        .bind(model_to_str(slot.model_type))
        .bind(source_to_str(slot.source))
        .bind(if slot.is_active { 1_i64 } else { 0_i64 })
        .bind(slot.created_at.to_rfc3339())
        .bind(slot.updated_at.to_rfc3339())
        .execute(&self.pool)
        .await?;

        Ok(slot.clone())
    }

    pub async fn update_slot(&self, slot: &SkinSlot) -> CoreResult<()> {
        sqlx::query(
            r#"
            UPDATE skin_slots
            SET name = ?, file_name = ?, original_file_name = ?,
                model_type = ?, source = ?, is_active = ?, updated_at = ?
            WHERE id = ?
            "#,
        )
        .bind(&slot.name)
        .bind(&slot.file_name)
        .bind(&slot.original_file_name)
        .bind(model_to_str(slot.model_type))
        .bind(source_to_str(slot.source))
        .bind(if slot.is_active { 1_i64 } else { 0_i64 })
        .bind(slot.updated_at.to_rfc3339())
        .bind(&slot.id)
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn delete_slot(&self, slot_id: &str) -> CoreResult<bool> {
        let result = sqlx::query("DELETE FROM skin_slots WHERE id = ?")
            .bind(slot_id)
            .execute(&self.pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }

    pub async fn set_active(&self, profile_id: &str, slot_id: &str) -> CoreResult<()> {
        let mut tx = self.pool.begin().await?;

        sqlx::query("UPDATE skin_slots SET is_active = 0 WHERE profile_id = ?")
            .bind(profile_id)
            .execute(&mut *tx)
            .await?;

        sqlx::query("UPDATE skin_slots SET is_active = 1 WHERE id = ? AND profile_id = ?")
            .bind(slot_id)
            .bind(profile_id)
            .execute(&mut *tx)
            .await?;

        tx.commit().await?;
        Ok(())
    }

    pub async fn reorder_slots(&self, profile_id: &str, ordered_ids: &[String]) -> CoreResult<()> {
        let mut tx = self.pool.begin().await?;
        let updated_at = Utc::now().to_rfc3339();

        for (index, slot_id) in ordered_ids.iter().enumerate() {
            sqlx::query(
                "UPDATE skin_slots SET slot_number = ?, updated_at = ? WHERE id = ? AND profile_id = ?",
            )
            .bind((index as i64) + 1)
            .bind(&updated_at)
            .bind(slot_id)
            .bind(profile_id)
            .execute(&mut *tx)
            .await?;
        }

        tx.commit().await?;
        Ok(())
    }

    pub async fn count_slots(&self, profile_id: &str) -> CoreResult<i64> {
        let row: (i64,) = sqlx::query_as(
            "SELECT COUNT(*) FROM skin_slots WHERE profile_id = ?",
        )
        .bind(profile_id)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.0)
    }
}