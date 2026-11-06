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

    #[error("integrity check failed: {0}")]
    Integrity(String),

    #[error("launch failed: {0}")]
    LaunchFailed(String),

    #[error("launch cancelled")]
    Cancelled,
}

impl From<std::io::Error> for LaunchError {
    fn from(value: std::io::Error) -> Self {
        LaunchError::Io(value.to_string())
    }
}

pub type LaunchResult<T> = Result<T, LaunchError>;
