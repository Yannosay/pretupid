use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;

use futures_util::stream::{self, StreamExt};
use sha1::{Digest, Sha1};
use tokio::io::AsyncWriteExt;
use tokio::sync::{mpsc, watch};

use crate::error::{LaunchError, LaunchResult};
use crate::launcher::LaunchEvent;

pub const USER_AGENT: &str = concat!("pretupid-launcher/", env!("CARGO_PKG_VERSION"));
const MAX_ATTEMPTS: u32 = 3;

#[derive(Debug, Clone)]
pub struct DownloadTask {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: u64,
}

impl DownloadTask {
    pub fn new(url: impl Into<String>, path: PathBuf, sha1: Option<String>, size: u64) -> Self {
        let sha1 = sha1
            .filter(|s| !s.trim().is_empty())
            .map(|s| s.to_lowercase());
        Self {
            url: url.into(),
            path,
            sha1,
            size,
        }
    }
}

pub fn http_client() -> LaunchResult<reqwest::Client> {
    reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(Duration::from_secs(20))
        .timeout(Duration::from_secs(600))
        .build()
        .map_err(|e| LaunchError::Network(e.to_string()))
}

pub fn is_safe_url(url: &str) -> bool {
    match url::Url::parse(url) {
        Ok(parsed) => parsed.scheme() == "https" || parsed.scheme() == "http",
        Err(_) => false,
    }
}

pub async fn sha1_of_file(path: &Path) -> std::io::Result<String> {
    let path = path.to_path_buf();
    tokio::task::spawn_blocking(move || {
        let mut file = std::fs::File::open(&path)?;
        let mut hasher = Sha1::new();
        std::io::copy(&mut file, &mut hasher)?;
        Ok(format!("{:x}", hasher.finalize()))
    })
    .await
    .map_err(|e| std::io::Error::other(e.to_string()))?
}

pub async fn file_is_valid(path: &Path, sha1: Option<&str>, size: u64, deep: bool) -> bool {
    let metadata = match tokio::fs::metadata(path).await {
        Ok(m) if m.is_file() => m,
        _ => return false,
    };
    if size > 0 && metadata.len() != size {
        return false;
    }
    if deep {
        if let Some(expected) = sha1 {
            return match sha1_of_file(path).await {
                Ok(actual) => actual.eq_ignore_ascii_case(expected),
                Err(_) => false,
            };
        }
    }
    true
}

fn part_path(path: &Path) -> PathBuf {
    let mut name = path
        .file_name()
        .map(|n| n.to_os_string())
        .unwrap_or_default();
    name.push(".part");
    path.with_file_name(name)
}

async fn download_attempt(
    client: &reqwest::Client,
    task: &DownloadTask,
    counter: &AtomicU64,
    cancel_rx: &watch::Receiver<bool>,
) -> LaunchResult<u64> {
    if !is_safe_url(&task.url) {
        return Err(LaunchError::Network(format!(
            "refusing to download from invalid URL '{}'",
            task.url
        )));
    }
    if let Some(parent) = task.path.parent() {
        tokio::fs::create_dir_all(parent)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
    }

    let response = client
        .get(&task.url)
        .send()
        .await
        .map_err(|e| LaunchError::Network(format!("{}: {}", task.url, e)))?;
    if !response.status().is_success() {
        return Err(LaunchError::Network(format!(
            "download failed with status {} for {}",
            response.status(),
            task.url
        )));
    }

    let temp = part_path(&task.path);
    let mut file = tokio::fs::File::create(&temp)
        .await
        .map_err(|e| LaunchError::Io(e.to_string()))?;
    let mut hasher = Sha1::new();
    let mut written: u64 = 0;
    let mut stream = response.bytes_stream();

    while let Some(chunk) = stream.next().await {
        if *cancel_rx.borrow() {
            drop(file);
            let _ = tokio::fs::remove_file(&temp).await;
            return Err(LaunchError::Cancelled);
        }
        let bytes = match chunk {
            Ok(b) => b,
            Err(e) => {
                drop(file);
                let _ = tokio::fs::remove_file(&temp).await;
                return Err(LaunchError::Network(e.to_string()));
            }
        };
        hasher.update(&bytes);
        written += bytes.len() as u64;
        counter.fetch_add(bytes.len() as u64, Ordering::Relaxed);
        if let Err(e) = file.write_all(&bytes).await {
            drop(file);
            let _ = tokio::fs::remove_file(&temp).await;
            return Err(LaunchError::Io(e.to_string()));
        }
    }

    file.flush()
        .await
        .map_err(|e| LaunchError::Io(e.to_string()))?;
    drop(file);

    if let Some(expected) = &task.sha1 {
        let actual = format!("{:x}", hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(&temp).await;
            return Err(LaunchError::Integrity(format!(
                "checksum mismatch for {} (expected {}, got {})",
                task.url, expected, actual
            )));
        }
    } else if task.size > 0 && written != task.size {
        let _ = tokio::fs::remove_file(&temp).await;
        return Err(LaunchError::Integrity(format!(
            "size mismatch for {} (expected {}, got {})",
            task.url, task.size, written
        )));
    }

    tokio::fs::rename(&temp, &task.path)
        .await
        .map_err(|e| LaunchError::Io(e.to_string()))?;
    Ok(written)
}

pub async fn download(
    client: &reqwest::Client,
    task: &DownloadTask,
    counter: &AtomicU64,
    cancel_rx: &watch::Receiver<bool>,
) -> LaunchResult<()> {
    let mut last_error = None;
    for attempt in 0..MAX_ATTEMPTS {
        let before = counter.load(Ordering::Relaxed);
        match download_attempt(client, task, counter, cancel_rx).await {
            Ok(_) => return Ok(()),
            Err(LaunchError::Cancelled) => return Err(LaunchError::Cancelled),
            Err(err) => {
                let after = counter.load(Ordering::Relaxed);
                counter.fetch_sub(after.saturating_sub(before), Ordering::Relaxed);
                last_error = Some(err);
                if attempt + 1 < MAX_ATTEMPTS {
                    tokio::time::sleep(Duration::from_millis(500 * u64::from(attempt + 1))).await;
                }
            }
        }
    }
    Err(last_error
        .unwrap_or_else(|| LaunchError::Network(format!("failed to download {}", task.url))))
}

pub async fn download_single(
    client: &reqwest::Client,
    task: &DownloadTask,
    cancel_rx: &watch::Receiver<bool>,
) -> LaunchResult<()> {
    let counter = AtomicU64::new(0);
    download(client, task, &counter, cancel_rx).await
}

pub async fn download_all(
    client: &reqwest::Client,
    tasks: Vec<DownloadTask>,
    label: &str,
    event_tx: &mpsc::UnboundedSender<LaunchEvent>,
    cancel_rx: &watch::Receiver<bool>,
    concurrency: usize,
) -> LaunchResult<()> {
    if tasks.is_empty() {
        return Ok(());
    }

    let total_size: u64 = tasks.iter().map(|t| t.size).sum();
    let total_files = tasks.len() as u64;
    let downloaded = Arc::new(AtomicU64::new(0));
    let finished = Arc::new(AtomicU64::new(0));
    let (done_tx, mut done_rx) = watch::channel(false);

    let reporter = {
        let downloaded = downloaded.clone();
        let finished = finished.clone();
        let event_tx = event_tx.clone();
        let label = label.to_string();
        tokio::spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_millis(200));
            loop {
                tokio::select! {
                    changed = done_rx.changed() => {
                        if changed.is_err() || *done_rx.borrow() { break; }
                    }
                    _ = interval.tick() => {
                        let current = downloaded.load(Ordering::Relaxed);
                        let files = finished.load(Ordering::Relaxed);
                        let message = if total_size > 0 {
                            format!(
                                "{} ({} / {} MB, {}/{} files)",
                                label,
                                current / 1_048_576,
                                total_size.max(1) / 1_048_576,
                                files,
                                total_files
                            )
                        } else {
                            format!("{} ({}/{} files)", label, files, total_files)
                        };
                        let (current, total) = if total_size > 0 {
                            (current.min(total_size), total_size)
                        } else {
                            (files, total_files)
                        };
                        event_tx.send(LaunchEvent::Progress { current, total, message }).ok();
                    }
                }
            }
        })
    };

    let results = stream::iter(tasks.into_iter().map(|task| {
        let downloaded = downloaded.clone();
        let finished = finished.clone();
        let cancel_rx = cancel_rx.clone();
        let client = client.clone();
        async move {
            let result = download(&client, &task, &downloaded, &cancel_rx).await;
            finished.fetch_add(1, Ordering::Relaxed);
            result
        }
    }))
    .buffer_unordered(concurrency.max(1))
    .collect::<Vec<_>>()
    .await;

    let _ = done_tx.send(true);
    let _ = reporter.await;

    if *cancel_rx.borrow() {
        return Err(LaunchError::Cancelled);
    }

    let mut failures: Vec<String> = Vec::new();
    for result in results {
        if let Err(err) = result {
            if matches!(err, LaunchError::Cancelled) {
                return Err(err);
            }
            failures.push(err.to_string());
        }
    }
    if !failures.is_empty() {
        let shown: Vec<String> = failures.iter().take(3).cloned().collect();
        return Err(LaunchError::Network(format!(
            "{} file(s) failed to download: {}",
            failures.len(),
            shown.join("; ")
        )));
    }

    event_tx
        .send(LaunchEvent::Progress {
            current: total_size.max(total_files),
            total: total_size.max(total_files),
            message: format!("{} complete", label),
        })
        .ok();
    Ok(())
}
