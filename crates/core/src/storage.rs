use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use sqlx::SqlitePool;
use std::str::FromStr;

use crate::error::CoreResult;

pub async fn create_pool(database_path: &str) -> CoreResult<SqlitePool> {
    let options = SqliteConnectOptions::from_str(database_path)?.create_if_missing(true);

    let pool = SqlitePoolOptions::new()
        .max_connections(5)
        .connect_with(options)
        .await?;

    Ok(pool)
}
