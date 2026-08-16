use mc_launch::version::VersionResolver;
use pretupid_core::instance::InstanceRepository;
use pretupid_core::settings::SettingsRepository;
use sqlx::SqlitePool;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Mutex;
use tokio::sync::watch;

pub struct AppState {
    pub pool: SqlitePool,
    pub instances: InstanceRepository,
    pub settings: SettingsRepository,
    pub resolver: VersionResolver,
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

        let settings = SettingsRepository::new(pool.clone());
        settings.init_schema().await?;

        let resolver = VersionResolver::new(resolver_base_dir);

        Ok(Self {
            pool,
            instances,
            settings,
            resolver,
            profiles_dir,
            caches_dir,
            cancel_channels: Mutex::new(HashMap::new()),
        })
    }
}