use mc_launch::version::VersionResolver;
use modrinth_client::ModrinthClient;
use pretupid_core::instance::InstanceRepository;
use pretupid_core::profile::SkinRepository;
use pretupid_core::settings::SettingsRepository;
use sqlx::SqlitePool;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Mutex;
use tokio::sync::watch;

pub struct AppState {
    pub pool: SqlitePool,
    pub instances: InstanceRepository,
    pub skins: SkinRepository,
    pub settings: SettingsRepository,
    pub resolver: VersionResolver,
    pub modrinth: ModrinthClient,
    pub profiles_dir: PathBuf,
    pub caches_dir: PathBuf,
    pub cancel_channels: Mutex<HashMap<String, watch::Sender<bool>>>,
}

impl AppState {
    pub async fn new(
        database_path: &str,
        resolver_base_dir: PathBuf,
        profiles_dir: PathBuf,
        caches_dir: PathBuf,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let pool = pretupid_core::storage::create_pool(database_path).await?;
        let instances = InstanceRepository::new(pool.clone());
        instances.init_schema().await?;
        instances.init_mods_schema().await?;

        let settings = SettingsRepository::new(pool.clone());
        settings.init_schema().await?;

        let skins = SkinRepository::new(pool.clone());
        skins.init_schema().await?;

        let resolver = VersionResolver::new(resolver_base_dir);
        let modrinth = ModrinthClient::new()?;

        Ok(Self {
            pool,
            instances,
            skins,
            settings,
            resolver,
            modrinth,
            profiles_dir,
            caches_dir,
            cancel_channels: Mutex::new(HashMap::new()),
        })
    }
}