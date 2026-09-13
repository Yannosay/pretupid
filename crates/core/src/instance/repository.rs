use sqlx::SqlitePool;
use uuid::Uuid;

use crate::error::CoreResult;
use super::models::{CreateInstanceRequest, Instance, InstanceMod, InstanceSettings, ModLoader, UpdateInstanceRequest};

pub struct InstanceRepository {
    pool: SqlitePool,
}

fn sanitize_folder_name(name: &str) -> String {
    let mut result: String = name
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
        .collect();
    if result.is_empty() {
        result.push_str("instance");
    }
    result
}

fn loader_to_str(loader: &ModLoader) -> &'static str {
    match loader {
        ModLoader::Vanilla => "vanilla",
        ModLoader::Forge => "forge",
        ModLoader::Fabric => "fabric",
        ModLoader::Quilt => "quilt",
        ModLoader::NeoForge => "neoforge",
        ModLoader::Modrinth => "modrinth",
    }
}

fn str_to_loader(value: &str) -> ModLoader {
    match value {
        "forge" => ModLoader::Forge,
        "fabric" => ModLoader::Fabric,
        "quilt" => ModLoader::Quilt,
        "neoforge" => ModLoader::NeoForge,
        "modrinth" => ModLoader::Modrinth,
        _ => ModLoader::Vanilla,
    }
}

fn map_instance_row(
    (id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at): (
        String,
        String,
        String,
        String,
        String,
        Option<String>,
        String,
        String,
        String,
    ),
) -> Instance {
    let created_at = chrono::DateTime::parse_from_rfc3339(&created_at)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .unwrap_or_else(|_| chrono::Utc::now());
    let updated_at = chrono::DateTime::parse_from_rfc3339(&updated_at)
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .unwrap_or_else(|_| chrono::Utc::now());

    Instance {
        id: Uuid::parse_str(&id).unwrap_or_else(|_| Uuid::nil()),
        name,
        description,
        minecraft_version,
        mod_loader: str_to_loader(&mod_loader),
        loader_version,
        folder_name,
        created_at,
        updated_at,
    }
}

fn map_mod_row(
    (id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name, created_at): (
        String,
        String,
        String,
        String,
        String,
        Option<String>,
        Option<String>,
        Option<String>,
        Option<String>,
        Option<String>,
        String,
    ),
) -> InstanceMod {
    InstanceMod {
        id,
        instance_id,
        project_id,
        project_type,
        title,
        icon_url,
        version_id,
        version_name,
        file_url,
        file_name,
        created_at,
    }
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

        let _ = sqlx::query("ALTER TABLE instances ADD COLUMN folder_name TEXT NOT NULL DEFAULT ''")
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

        self.init_mods_schema().await?;

        let _ = sqlx::query("ALTER TABLE instance_mods ADD COLUMN file_name TEXT")
            .execute(&self.pool)
            .await;

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
        let row: Option<(
            String,
            String,
            String,
            String,
            String,
            Option<String>,
            String,
            String,
            String,
        )> = sqlx::query_as(
            r#"
            SELECT id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at
            FROM instances WHERE id = ?
            "#,
        )
        .bind(id.to_string())
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(map_instance_row))
    }

    pub async fn list(&self) -> CoreResult<Vec<Instance>> {
        let rows: Vec<(
            String,
            String,
            String,
            String,
            String,
            Option<String>,
            String,
            String,
            String,
        )> = sqlx::query_as(
            r#"
            SELECT id, name, description, minecraft_version, mod_loader, loader_version, folder_name, created_at, updated_at
            FROM instances ORDER BY created_at DESC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(map_instance_row).collect())
    }

    pub async fn update(
        &self,
        id: Uuid,
        req: UpdateInstanceRequest,
    ) -> CoreResult<Option<Instance>> {
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

    pub async fn save_settings(
        &self,
        instance_id: Uuid,
        settings: &InstanceSettings,
    ) -> CoreResult<()> {
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
        .bind(if settings.fullscreen { 1_i64 } else { 0_i64 })
        .bind(&settings.java_path)
        .bind(serde_json::to_string(&settings.extra_jvm_args).unwrap_or_else(|_| "[]".to_string()))
        .bind(serde_json::to_string(&settings.extra_game_args).unwrap_or_else(|_| "[]".to_string()))
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn get_settings(&self, instance_id: Uuid) -> CoreResult<InstanceSettings> {
        let row: Option<(i64, i64, i64, i64, i64, Option<String>, String, String)> = sqlx::query_as(
            r#"
            SELECT memory_min_mb, memory_max_mb, resolution_width, resolution_height, fullscreen, java_path, extra_jvm_args, extra_game_args
            FROM instance_settings WHERE instance_id = ?
            "#,
        )
        .bind(instance_id.to_string())
        .fetch_optional(&self.pool)
        .await?;

        match row {
            Some((
                memory_min_mb,
                memory_max_mb,
                resolution_width,
                resolution_height,
                fullscreen,
                java_path,
                extra_jvm_args,
                extra_game_args,
            )) => Ok(InstanceSettings {
                memory_min_mb: memory_min_mb as u32,
                memory_max_mb: memory_max_mb as u32,
                resolution_width: resolution_width as u32,
                resolution_height: resolution_height as u32,
                fullscreen: fullscreen != 0,
                java_path,
                extra_jvm_args: serde_json::from_str(&extra_jvm_args).unwrap_or_default(),
                extra_game_args: serde_json::from_str(&extra_game_args).unwrap_or_default(),
            }),
            None => Ok(InstanceSettings::default()),
        }
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

        Ok(())
    }

    pub async fn list_mods(&self, instance_id: &str) -> CoreResult<Vec<InstanceMod>> {
        let rows: Vec<(
            String,
            String,
            String,
            String,
            String,
            Option<String>,
            Option<String>,
            Option<String>,
            Option<String>,
            Option<String>,
            String,
        )> = sqlx::query_as(
            "SELECT id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name, created_at
             FROM instance_mods WHERE instance_id = ? ORDER BY created_at DESC",
        )
        .bind(instance_id)
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(map_mod_row).collect())
    }

    pub async fn add_mod(
        &self,
        instance_id: &str,
        project_id: &str,
        project_type: &str,
        title: &str,
        icon_url: Option<&str>,
        version_id: Option<&str>,
        version_name: Option<&str>,
        file_url: Option<&str>,
        file_name: Option<&str>,
    ) -> CoreResult<InstanceMod> {
        let id = Uuid::new_v4().to_string();

        sqlx::query(
            "INSERT INTO instance_mods (id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        .bind(&id)
        .bind(instance_id)
        .bind(project_id)
        .bind(project_type)
        .bind(title)
        .bind(icon_url)
        .bind(version_id)
        .bind(version_name)
        .bind(file_url)
        .bind(file_name)
        .execute(&self.pool)
        .await?;

        let row: (
            String,
            String,
            String,
            String,
            String,
            Option<String>,
            Option<String>,
            Option<String>,
            Option<String>,
            Option<String>,
            String,
        ) = sqlx::query_as(
            "SELECT id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name, created_at
             FROM instance_mods WHERE id = ?",
        )
        .bind(&id)
        .fetch_one(&self.pool)
        .await?;

        Ok(map_mod_row(row))
    }

    pub async fn get_mod_by_id(&self, mod_id: &str) -> CoreResult<Option<InstanceMod>> {
        let row: Option<(
            String, String, String, String, String, Option<String>, Option<String>, Option<String>, Option<String>, Option<String>, String
        )> = sqlx::query_as(
            "SELECT id, instance_id, project_id, project_type, title, icon_url, version_id, version_name, file_url, file_name, created_at
             FROM instance_mods WHERE id = ?"
        )
        .bind(mod_id)
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(map_mod_row))
    }

    pub async fn remove_mod(&self, mod_id: &str) -> CoreResult<bool> {
        let result = sqlx::query("DELETE FROM instance_mods WHERE id = ?")
            .bind(mod_id)
            .execute(&self.pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }
}