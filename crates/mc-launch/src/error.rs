use thiserror::Error;

#[derive(Debug, Error)]
pub enum LaunchError {
    #[error("network error: {0}")]
    Network(String),

    #[error("parse error: {0}")]
    Parse(String),

    #[error("io error: {0}")]
    Io(String),

    #[error("not found: {0}")]
    NotFound(String),

    #[error("launch failed: {0}")]
    LaunchFailed(String),

    #[error("launch cancelled")]
    Cancelled,
}

pub type LaunchResult<T> = Result<T, LaunchError>;