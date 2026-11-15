use sqlx::SqlitePool;
use uuid::Uuid;

use super::models::{
    CreateInstanceRequest, Instance, InstanceMod, InstanceSettings, ModLoader, NewInstanceMod,
    UpdateInstanceRequest,
};
use crate::error::CoreResult;

const RESERVED_FOLDERS: &[&str] = &["skins", "con", "prn", "aux", "nul"];

const INSTANCE_COLUMNS: &str = "id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at, last_played, playtime_seconds, icon_url, group_name, icon_style";

const MOD_COLUMNS: &str = "id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name, created_at, enabled, sha1";

pub struct InstanceRepository {
    pool: SqlitePool,
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
    last_played: Option<String>,
    playtime_seconds: i64,
    icon_url: Option<String>,
    group_name: Option<String>,
    icon_style: Option<String>,
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
    quick_play_server: Option<String>,
}

fn parse_time(value: &str) -> Option<chrono::DateTime<chrono::Utc>> {
    chrono::DateTime::parse_from_rfc3339(value)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .ok()
}

impl From<InstanceRow> for Instance {
    fn from(row: InstanceRow) -> Self {
        Instance {
            id: Uuid::parse_str(&row.id).unwrap_or_else(|_| Uuid::nil()),
            name: row.name,
            description: row.description,
            minecraft_version: row.minecraft_version,
            mod_loader: ModLoader::parse(&row.mod_loader),
            loader_version: row.loader_version.filter(|v| !v.trim().is_empty()),
            folder_name: row.folder_name,
            created_at: parse_time(&row.created_at).unwrap_or_else(chrono::Utc::now),
            updated_at: parse_time(&row.updated_at).unwrap_or_else(chrono::Utc::now),
            last_played: row.last_played.as_deref().and_then(parse_time),
            playtime_seconds: row.playtime_seconds,
            icon_url: row.icon_url,
            group_name: row.group_name.filter(|g| !g.trim().is_empty()),
            icon_style: row.icon_style.filter(|s| !s.trim().is_empty()),
        }
    }
}

pub fn sanitize_folder_name(name: &str) -> String {
    let mut result: String = name
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '-' || c == '_' {
                c
            } else {
                '_'
            }
        })
        .take(48)
        .collect();
    if result.trim_matches('_').is_empty() {
        result = "instance".to_string();
    }
    result
}

impl InstanceRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    async fn add_column(&self, table: &str, definition: &str) {
        let _ = sqlx::query(&format!("ALTER TABLE {table} ADD COLUMN {definition}"))
            .execute(&self.pool)
            .await;
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

        self.add_column("instances", "folder_name TEXT NOT NULL DEFAULT ''")
            .await;
        self.add_column("instances", "last_played TEXT").await;
        self.add_column("instances", "playtime_seconds INTEGER NOT NULL DEFAULT 0")
            .await;
        self.add_column("instances", "icon_url TEXT").await;
        self.add_column("instances", "group_name TEXT").await;
        self.add_column("instances", "icon_style TEXT").await;

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

        self.add_column("instance_settings", "quick_play_server TEXT")
            .await;

        self.init_mods_schema().await?;
        Ok(())
    }

    pub async fn init_mods_schema(&self) -> CoreResult<()> {
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS instance_mods (
                id TEXT PRIMARY KEY,
                instance_id TEXT NOT NULL,
                project_id TEXT NOT NULL,
                project_type TEXT NOT NULL,
                title TEXT NOT NULL,
                icon_url TEXT,
                version_id TEXT,
                version_name TEXT,
                file_url TEXT,
                file_name TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )",
        )
        .execute(&self.pool)
        .await?;

        self.add_column("instance_mods", "file_name TEXT").await;
        self.add_column("instance_mods", "enabled INTEGER NOT NULL DEFAULT 1")
            .await;
        self.add_column("instance_mods", "sha1 TEXT").await;

        sqlx::query(
            "CREATE INDEX IF NOT EXISTS idx_instance_mods_instance ON instance_mods(instance_id)",
        )
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn folder_taken(&self, folder: &str) -> CoreResult<bool> {
        if RESERVED_FOLDERS.contains(&folder.to_lowercase().as_str()) {
            return Ok(true);
        }
        let exists: (i64,) =
            sqlx::query_as("SELECT COUNT(*) FROM instances WHERE lower(folder_name) = lower(?)")
                .bind(folder)
                .fetch_one(&self.pool)
                .await?;
        Ok(exists.0 > 0)
    }

    pub async fn create(&self, req: CreateInstanceRequest) -> CoreResult<Instance> {
        let id = Uuid::new_v4();
        let now = chrono::Utc::now();

        let base_folder = sanitize_folder_name(&req.name);
        let mut folder_name = base_folder.clone();
        let mut counter = 1;
        while self.folder_taken(&folder_name).await? {
            folder_name = format!("{}_{}", base_folder, counter);
            counter += 1;
        }

        sqlx::query(
            r#"
            INSERT INTO instances (id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at, playtime_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            "#,
        )
        .bind(id.to_string())
        .bind(&req.name)
        .bind(&req.description)
        .bind(&req.minecraft_version)
        .bind(req.mod_loader.as_str())
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
            mod_loader: req.mod_loader,
            loader_version: req.loader_version,
            folder_name,
            created_at: now,
            updated_at: now,
            last_played: None,
            playtime_seconds: 0,
            icon_url: None,
            group_name: None,
            icon_style: None,
        })
    }

    pub async fn get_by_id(&self, id: Uuid) -> CoreResult<Option<Instance>> {
        let row: Option<InstanceRow> = sqlx::query_as(&format!(
            "SELECT {INSTANCE_COLUMNS} FROM instances WHERE id = ?"
        ))
        .bind(id.to_string())
        .fetch_optional(&self.pool)
        .await?;
        Ok(row.map(Instance::from))
    }

    pub async fn list(&self) -> CoreResult<Vec<Instance>> {
        let rows: Vec<InstanceRow> = sqlx::query_as(&format!(
            "SELECT {INSTANCE_COLUMNS} FROM instances ORDER BY COALESCE(last_played, created_at) DESC"
        ))
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(Instance::from).collect())
    }

    pub async fn update(
        &self,
        id: Uuid,
        req: UpdateInstanceRequest,
    ) -> CoreResult<Option<Instance>> {
        let existing = match self.get_by_id(id).await? {
            Some(e) => e,
            None => return Ok(None),
        };

        let name = req
            .name
            .filter(|n| !n.trim().is_empty())
            .unwrap_or(existing.name);
        let description = req.description.unwrap_or(existing.description);
        let minecraft_version = req.minecraft_version.unwrap_or(existing.minecraft_version);
        let mod_loader = req.mod_loader.unwrap_or(existing.mod_loader);
        let loader_version = match req.loader_version {
            Some(v) if v.trim().is_empty() => None,
            Some(v) => Some(v),
            None => existing.loader_version,
        };
        let icon_url = match req.icon_url {
            Some(v) if v.trim().is_empty() => None,
            Some(v) => Some(v),
            None => existing.icon_url,
        };
        let group_name = match req.group_name {
            Some(v) if v.trim().is_empty() => None,
            Some(v) => Some(v.trim().to_string()),
            None => existing.group_name,
        };
        let icon_style = match req.icon_style {
            Some(v) if v.trim().is_empty() => None,
            Some(v) => Some(v.trim().to_string()),
            None => existing.icon_style,
        };
        let updated_at = chrono::Utc::now();

        sqlx::query(
            r#"
            UPDATE instances
            SET name = ?, description = ?, minecraft_version = ?, mod_loader = ?, loader_version = ?, icon_url = ?, group_name = ?, icon_style = ?, updated_at = ?
            WHERE id = ?
            "#,
        )
        .bind(&name)
        .bind(&description)
        .bind(&minecraft_version)
        .bind(mod_loader.as_str())
        .bind(&loader_version)
        .bind(&icon_url)
        .bind(&group_name)
        .bind(&icon_style)
        .bind(updated_at.to_rfc3339())
        .bind(id.to_string())
        .execute(&self.pool)
        .await?;

        self.get_by_id(id).await
    }

    pub async fn record_play_session(
        &self,
        id: Uuid,
        started: chrono::DateTime<chrono::Utc>,
        seconds: i64,
    ) -> CoreResult<()> {
        sqlx::query("UPDATE instances SET last_played = ?, playtime_seconds = playtime_seconds + ? WHERE id = ?")
            .bind(started.to_rfc3339())
            .bind(seconds.max(0))
            .bind(id.to_string())
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    pub async fn delete(&self, id: Uuid) -> CoreResult<bool> {
        sqlx::query("DELETE FROM instance_mods WHERE instance_id = ?")
            .bind(id.to_string())
            .execute(&self.pool)
            .await?;
        sqlx::query("DELETE FROM instance_settings WHERE instance_id = ?")
            .bind(id.to_string())
            .execute(&self.pool)
            .await?;
        let result = sqlx::query("DELETE FROM instances WHERE id = ?")
            .bind(id.to_string())
            .execute(&self.pool)
            .await?;
        Ok(result.rows_affected() > 0)
    }

    pub async fn save_settings(
        &self,
        instance_id: Uuid,
        settings: &InstanceSettings,
    ) -> CoreResult<()> {
        sqlx::query(
            r#"
            INSERT OR REPLACE INTO instance_settings
            (instance_id, memory_min_mb, memory_max_mb, resolution_width, resolution_height, fullscreen, java_path, extra_jvm_args, extra_game_args, quick_play_server)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(instance_id.to_string())
        .bind(i64::from(settings.memory_min_mb))
        .bind(i64::from(settings.memory_max_mb))
        .bind(i64::from(settings.resolution_width))
        .bind(i64::from(settings.resolution_height))
        .bind(i64::from(settings.fullscreen))
        .bind(settings.java_path.as_deref().map(str::trim).filter(|p| !p.is_empty()))
        .bind(serde_json::to_string(&settings.extra_jvm_args).unwrap_or_else(|_| "[]".to_string()))
        .bind(serde_json::to_string(&settings.extra_game_args).unwrap_or_else(|_| "[]".to_string()))
        .bind(settings.quick_play_server.as_deref().map(str::trim).filter(|p| !p.is_empty()))
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn get_settings(&self, instance_id: Uuid) -> CoreResult<InstanceSettings> {
        let row: Option<SettingsRow> = sqlx::query_as(
            r#"
            SELECT memory_min_mb, memory_max_mb, resolution_width, resolution_height, fullscreen, java_path, extra_jvm_args, extra_game_args, quick_play_server
            FROM instance_settings WHERE instance_id = ?
            "#,
        )
        .bind(instance_id.to_string())
        .fetch_optional(&self.pool)
        .await?;

        Ok(match row {
            Some(r) => InstanceSettings {
                memory_min_mb: r.memory_min_mb.clamp(256, 262_144) as u32,
                memory_max_mb: r.memory_max_mb.clamp(512, 262_144) as u32,
                resolution_width: r.resolution_width.clamp(320, 16_384) as u32,
                resolution_height: r.resolution_height.clamp(240, 16_384) as u32,
                fullscreen: r.fullscreen != 0,
                java_path: r.java_path,
                extra_jvm_args: serde_json::from_str(&r.extra_jvm_args).unwrap_or_default(),
                extra_game_args: serde_json::from_str(&r.extra_game_args).unwrap_or_default(),
                quick_play_server: r.quick_play_server,
            },
            None => InstanceSettings::default(),
        })
    }

    pub async fn list_mods(&self, instance_id: &str) -> CoreResult<Vec<InstanceMod>> {
        let rows: Vec<InstanceMod> = sqlx::query_as(&format!(
            "SELECT {MOD_COLUMNS} FROM instance_mods WHERE instance_id = ? ORDER BY lower(title) ASC"
        ))
        .bind(instance_id)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows)
    }

    pub async fn add_mod(&self, new_mod: &NewInstanceMod) -> CoreResult<InstanceMod> {
        let id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO instance_mods (id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name, enabled, sha1)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        .bind(&id)
        .bind(&new_mod.instance_id)
        .bind(&new_mod.project_id)
        .bind(&new_mod.project_type)
        .bind(&new_mod.title)
        .bind(&new_mod.icon_url)
        .bind(&new_mod.version_id)
        .bind(&new_mod.version_name)
        .bind(&new_mod.file_url)
        .bind(&new_mod.file_name)
        .bind(new_mod.enabled)
        .bind(&new_mod.sha1)
        .execute(&self.pool)
        .await?;

        self.get_mod_by_id(&id).await?.ok_or_else(|| {
            crate::error::CoreError::NotFound("mod disappeared after insert".to_string())
        })
    }

    pub async fn get_mod_by_id(&self, mod_id: &str) -> CoreResult<Option<InstanceMod>> {
        let row: Option<InstanceMod> = sqlx::query_as(&format!(
            "SELECT {MOD_COLUMNS} FROM instance_mods WHERE id = ?"
        ))
        .bind(mod_id)
        .fetch_optional(&self.pool)
        .await?;
        Ok(row)
    }

    pub async fn remove_mod(&self, mod_id: &str) -> CoreResult<bool> {
        let result = sqlx::query("DELETE FROM instance_mods WHERE id = ?")
            .bind(mod_id)
            .execute(&self.pool)
            .await?;
        Ok(result.rows_affected() > 0)
    }

    pub async fn set_mod_enabled(&self, mod_id: &str, enabled: bool) -> CoreResult<()> {
        sqlx::query("UPDATE instance_mods SET enabled = ? WHERE id = ?")
            .bind(enabled)
            .bind(mod_id)
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    pub async fn replace_mod_file(
        &self,
        mod_id: &str,
        version_id: Option<&str>,
        version_name: Option<&str>,
        file_url: Option<&str>,
        file_name: &str,
        sha1: Option<&str>,
    ) -> CoreResult<()> {
        sqlx::query(
            "UPDATE instance_mods SET version_id = ?, version_name = ?, file_url = ?, file_name = ?, sha1 = ? WHERE id = ?",
        )
        .bind(version_id)
        .bind(version_name)
        .bind(file_url)
        .bind(file_name)
        .bind(sha1)
        .bind(mod_id)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn update_mod_identity(
        &self,
        mod_id: &str,
        project_id: &str,
        title: &str,
        icon_url: Option<&str>,
        version_id: Option<&str>,
        version_name: Option<&str>,
    ) -> CoreResult<()> {
        sqlx::query(
            "UPDATE instance_mods SET project_id = ?, title = ?, icon_url = COALESCE(?, icon_url), version_id = COALESCE(?, version_id), version_name = COALESCE(?, version_name) WHERE id = ?",
        )
        .bind(project_id)
        .bind(title)
        .bind(icon_url)
        .bind(version_id)
        .bind(version_name)
        .bind(mod_id)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn set_mod_sha1(&self, mod_id: &str, sha1: &str) -> CoreResult<()> {
        sqlx::query("UPDATE instance_mods SET sha1 = ? WHERE id = ?")
            .bind(sha1)
            .bind(mod_id)
            .execute(&self.pool)
            .await?;
        Ok(())
    }
}
