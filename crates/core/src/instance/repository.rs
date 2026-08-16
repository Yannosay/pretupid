use sqlx::SqlitePool;
use uuid::Uuid;

use crate::error::CoreResult;
use super::models::{CreateInstanceRequest, Instance, InstanceSettings, ModLoader, UpdateInstanceRequest};

pub struct InstanceRepository {
    pool: SqlitePool,
}

impl InstanceRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn init_schema(&self) -> CoreResult<()> {
        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS instances (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                minecraft_version TEXT NOT NULL,
                mod_loader TEXT NOT NULL DEFAULT 'vanilla',
                loader_version TEXT,
                folder_name TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            "#,
        )
        .execute(&self.pool)
        .await?;

        let _ = sqlx::query(
            "ALTER TABLE instances ADD COLUMN folder_name TEXT NOT NULL DEFAULT ''",
        )
        .execute(&self.pool)
        .await;

        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS instance_settings (
                instance_id TEXT PRIMARY KEY NOT NULL,
                memory_min_mb INTEGER NOT NULL DEFAULT 512,
                memory_max_mb INTEGER NOT NULL DEFAULT 4096,
                resolution_width INTEGER NOT NULL DEFAULT 1280,
                resolution_height INTEGER NOT NULL DEFAULT 720,
                fullscreen INTEGER NOT NULL DEFAULT 0,
                java_path TEXT,
                extra_jvm_args TEXT NOT NULL DEFAULT '[]',
                extra_game_args TEXT NOT NULL DEFAULT '[]',
                FOREIGN KEY (instance_id) REFERENCES instances(id) ON DELETE CASCADE
            );
            "#,
        )
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn create(&self, req: CreateInstanceRequest) -> CoreResult<Instance> {
        let id = Uuid::new_v4();
        let now = chrono::Utc::now();
        let loader = req.mod_loader.clone();

        let base_folder = sanitize_folder_name(&req.name);
        let mut folder_name = base_folder.clone();
        let mut counter = 1;
        loop {
            let exists: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM instances WHERE folder_name = ?")
                .bind(&folder_name)
                .fetch_one(&self.pool)
                .await?;
            if exists.0 == 0 {
                break;
            }
            folder_name = format!("{}_{}", base_folder, counter);
            counter += 1;
        }

        sqlx::query(
            r#"
            INSERT INTO instances (id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(id.to_string())
        .bind(&req.name)
        .bind(&req.description)
        .bind(&req.minecraft_version)
        .bind(loader_to_str(&loader))
        .bind(&req.loader_version)
        .bind(&folder_name)
        .bind(now.to_rfc3339())
        .bind(now.to_rfc3339())
        .execute(&self.pool)
        .await?;

        Ok(Instance {
            id,
            name: req.name,
            description: req.description,
            minecraft_version: req.minecraft_version,
            mod_loader: loader,
            loader_version: req.loader_version,
            folder_name,
            created_at: now,
            updated_at: now,
        })
    }

    pub async fn get_by_id(&self, id: Uuid) -> CoreResult<Option<Instance>> {
        let row = sqlx::query_as::<_, InstanceRow>(
            r#"
            SELECT id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at
            FROM instances WHERE id = ?
            "#,
        )
        .bind(id.to_string())
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(|r| r.into_instance()))
    }

    pub async fn list(&self) -> CoreResult<Vec<Instance>> {
        let rows = sqlx::query_as::<_, InstanceRow>(
            r#"
            SELECT id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at
            FROM instances ORDER BY created_at DESC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(|r| r.into_instance()).collect())
    }

    pub async fn update(&self, id: Uuid, req: UpdateInstanceRequest) -> CoreResult<Option<Instance>> {
        let existing = self.get_by_id(id).await?;
        if existing.is_none() {
            return Ok(None);
        }

        let existing = existing.unwrap();
        let name = req.name.unwrap_or(existing.name);
        let description = req.description.unwrap_or(existing.description);
        let minecraft_version = req.minecraft_version.unwrap_or(existing.minecraft_version);
        let mod_loader = req.mod_loader.unwrap_or(existing.mod_loader);
        let loader_version = req.loader_version.or(existing.loader_version);
        let updated_at = chrono::Utc::now();

        sqlx::query(
            r#"
            UPDATE instances
            SET name = ?, description = ?, minecraft_version = ?, mod_loader = ?, loader_version = ?, updated_at = ?
            WHERE id = ?
            "#,
        )
        .bind(&name)
        .bind(&description)
        .bind(&minecraft_version)
        .bind(loader_to_str(&mod_loader))
        .bind(&loader_version)
        .bind(updated_at.to_rfc3339())
        .bind(id.to_string())
        .execute(&self.pool)
        .await?;

        Ok(Some(Instance {
            id,
            name,
            description,
            minecraft_version,
            mod_loader,
            loader_version,
            folder_name: existing.folder_name,
            created_at: existing.created_at,
            updated_at,
        }))
    }

    pub async fn delete(&self, id: Uuid) -> CoreResult<bool> {
        let result = sqlx::query("DELETE FROM instances WHERE id = ?")
            .bind(id.to_string())
            .execute(&self.pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }

    pub async fn save_settings(&self, instance_id: Uuid, settings: &InstanceSettings) -> CoreResult<()> {
        sqlx::query(
            r#"
            INSERT OR REPLACE INTO instance_settings
            (instance_id, memory_min_mb, memory_max_mb, resolution_width, resolution_height, fullscreen, java_path, extra_jvm_args, extra_game_args)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(instance_id.to_string())
        .bind(settings.memory_min_mb as i64)
        .bind(settings.memory_max_mb as i64)
        .bind(settings.resolution_width as i64)
        .bind(settings.resolution_height as i64)
        .bind(if settings.fullscreen { 1 } else { 0 })
        .bind(&settings.java_path)
        .bind(serde_json::to_string(&settings.extra_jvm_args).unwrap_or_else(|_| "[]".to_string()))
        .bind(serde_json::to_string(&settings.extra_game_args).unwrap_or_else(|_| "[]".to_string()))
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn get_settings(&self, instance_id: Uuid) -> CoreResult<InstanceSettings> {
        let row = sqlx::query_as::<_, SettingsRow>(
            r#"
            SELECT memory_min_mb, memory_max_mb, resolution_width, resolution_height, fullscreen, java_path, extra_jvm_args, extra_game_args
            FROM instance_settings WHERE instance_id = ?
            "#,
        )
        .bind(instance_id.to_string())
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(|r| r.into_settings()).unwrap_or_default())
    }
}

fn sanitize_folder_name(name: &str) -> String {
    let trimmed = name.trim();
    let mut result = String::new();
    for c in trimmed.chars() {
        if c.is_ascii_alphanumeric()
            || c == ' '
            || c == '.'
            || c == '-'
            || c == '_'
            || c == '('
            || c == ')'
            || c == '['
            || c == ']'
            || c == '{'
            || c == '}'
            || c == '!'
            || c == '@'
            || c == '#'
            || c == '$'
            || c == '%'
            || c == '^'
            || c == '&'
            || c == '+'
            || c == '='
            || c == ';'
            || c == ','
            || c == '\''
            || c == '`'
            || c == '~'
        {
            result.push(c);
        } else {
            result.push(' ');
        }
    }
    result = result.trim().to_string();
    if result.is_empty() {
        result = "instance".to_string();
    }
    let mut chars: Vec<char> = result.chars().collect();
    while chars.last() == Some(&'.') || chars.last() == Some(&' ') {
        chars.pop();
    }
    if chars.len() > 64 {
        chars.truncate(64);
    }
    chars.into_iter().collect()
}

#[derive(sqlx::FromRow)]
struct InstanceRow {
    id: String,
    name: String,
    description: String,
    minecraft_version: String,
    mod_loader: String,
    loader_version: Option<String>,
    folder_name: String,
    created_at: String,
    updated_at: String,
}

impl InstanceRow {
    fn into_instance(self) -> Instance {
        Instance {
            id: Uuid::parse_str(&self.id).unwrap_or_else(|_| Uuid::nil()),
            name: self.name,
            description: self.description,
            minecraft_version: self.minecraft_version,
            mod_loader: str_to_loader(&self.mod_loader),
            loader_version: self.loader_version,
            folder_name: self.folder_name,
            created_at: chrono::DateTime::parse_from_rfc3339(&self.created_at)
                .map(|dt| dt.with_timezone(&chrono::Utc))
                .unwrap_or_else(|_| chrono::Utc::now()),
            updated_at: chrono::DateTime::parse_from_rfc3339(&self.updated_at)
                .map(|dt| dt.with_timezone(&chrono::Utc))
                .unwrap_or_else(|_| chrono::Utc::now()),
        }
    }
}

#[derive(sqlx::FromRow)]
struct SettingsRow {
    memory_min_mb: i64,
    memory_max_mb: i64,
    resolution_width: i64,
    resolution_height: i64,
    fullscreen: i64,
    java_path: Option<String>,
    extra_jvm_args: String,
    extra_game_args: String,
}

impl SettingsRow {
    fn into_settings(self) -> InstanceSettings {
        InstanceSettings {
            memory_min_mb: self.memory_min_mb as u32,
            memory_max_mb: self.memory_max_mb as u32,
            resolution_width: self.resolution_width as u32,
            resolution_height: self.resolution_height as u32,
            fullscreen: self.fullscreen != 0,
            java_path: self.java_path,
            extra_jvm_args: serde_json::from_str(&self.extra_jvm_args).unwrap_or_default(),
            extra_game_args: serde_json::from_str(&self.extra_game_args).unwrap_or_default(),
        }
    }
}

fn loader_to_str(loader: &ModLoader) -> &'static str {
    match loader {
        ModLoader::Vanilla => "vanilla",
        ModLoader::Forge => "forge",
        ModLoader::Fabric => "fabric",
        ModLoader::Quilt => "quilt",
    }
}

fn str_to_loader(s: &str) -> ModLoader {
    match s {
        "forge" => ModLoader::Forge,
        "fabric" => ModLoader::Fabric,
        "quilt" => ModLoader::Quilt,
        _ => ModLoader::Vanilla,
    }
}