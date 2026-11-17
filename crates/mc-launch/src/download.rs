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
const MAX_ATTEMPTS: u32 = 6;
const MAX_BACKOFF_MS: u64 = 8_000;
const RETRY_PASS_CONCURRENCY: usize = 2;
const STALL_TIMEOUT: Duration = Duration::from_secs(25);
const VALIDATION_CONCURRENCY: usize = 64;

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
        .connect_timeout(Duration::from_secs(15))
        .read_timeout(STALL_TIMEOUT)
        .timeout(Duration::from_secs(900))
        .pool_max_idle_per_host(64)
        .pool_idle_timeout(Duration::from_secs(60))
        .tcp_keepalive(Duration::from_secs(30))
        .tcp_nodelay(true)
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

async fn wait_cancelled(cancel: &mut watch::Receiver<bool>) {
    if cancel.wait_for(|cancelled| *cancelled).await.is_err() {
        std::future::pending::<()>().await;
    }
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

    let temp = part_path(&task.path);
    let resume_from = resumable_bytes(&temp, task.size).await;

    let mut cancel = cancel_rx.clone();
    let mut request = client.get(&task.url);
    if resume_from > 0 {
        request = request.header(reqwest::header::RANGE, format!("bytes={resume_from}-"));
    }
    let response = tokio::select! {
        response = request.send() => {
            response.map_err(|e| LaunchError::Network(format!("{}: {}", task.url, describe_request_error(&e))))?
        }
        _ = wait_cancelled(&mut cancel) => return Err(LaunchError::Cancelled),
    };
    if !response.status().is_success() {
        return Err(LaunchError::Network(format!(
            "download failed with status {} for {}",
            response.status(),
            task.url
        )));
    }

    let resumed = resume_from > 0 && response.status() == reqwest::StatusCode::PARTIAL_CONTENT;
    let (mut file, mut hasher, mut written) = if resumed {
        let hasher = hash_prefix(&temp).await?;
        let file = tokio::fs::OpenOptions::new()
            .append(true)
            .open(&temp)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        counter.fetch_add(resume_from, Ordering::Relaxed);
        (file, hasher, resume_from)
    } else {
        let file = tokio::fs::File::create(&temp)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;
        (file, Sha1::new(), 0)
    };
    let mut stream = response.bytes_stream();

    loop {
        let next = tokio::select! {
            next = tokio::time::timeout(STALL_TIMEOUT, stream.next()) => next,
            _ = wait_cancelled(&mut cancel) => {
                let _ = file.flush().await;
                return Err(LaunchError::Cancelled);
            }
        };
        let chunk = match next {
            Ok(Some(chunk)) => chunk,
            Ok(None) => break,
            Err(_) => {
                let _ = file.flush().await;
                return Err(LaunchError::Network(format!(
                    "download stalled for {}",
                    task.url
                )));
            }
        };
        let bytes = match chunk {
            Ok(b) => b,
            Err(e) => {
                let _ = file.flush().await;
                return Err(LaunchError::Network(format!(
                    "{}: {}",
                    task.url,
                    describe_request_error(&e)
                )));
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

async fn resumable_bytes(temp: &Path, expected_size: u64) -> u64 {
    let existing = match tokio::fs::metadata(temp).await {
        Ok(metadata) if metadata.is_file() => metadata.len(),
        _ => return 0,
    };
    if existing > 0 && expected_size > 0 && existing < expected_size {
        return existing;
    }
    if existing > 0 {
        let _ = tokio::fs::remove_file(temp).await;
    }
    0
}

async fn hash_prefix(path: &Path) -> LaunchResult<Sha1> {
    let path = path.to_path_buf();
    tokio::task::spawn_blocking(move || {
        let mut file = std::fs::File::open(&path)?;
        let mut hasher = Sha1::new();
        std::io::copy(&mut file, &mut hasher)?;
        Ok::<Sha1, std::io::Error>(hasher)
    })
    .await
    .map_err(|e| LaunchError::Io(e.to_string()))?
    .map_err(|e| LaunchError::Io(e.to_string()))
}

fn describe_request_error(error: &reqwest::Error) -> String {
    let mut message = error.to_string();
    let mut source = std::error::Error::source(error);
    while let Some(cause) = source {
        let text = cause.to_string();
        if !message.contains(&text) {
            message.push_str(": ");
            message.push_str(&text);
        }
        source = cause.source();
    }
    message
}

fn backoff(attempt: u32, salt: usize) -> Duration {
    let base = (500u64 << attempt.min(5)).min(MAX_BACKOFF_MS);
    let jitter = (salt as u64).wrapping_mul(2_654_435_761) % (base / 2 + 1);
    Duration::from_millis(base / 2 + jitter)
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
                let fatal = matches!(err, LaunchError::Io(_));
                last_error = Some(err);
                if fatal {
                    break;
                }
                if attempt + 1 < MAX_ATTEMPTS {
                    let mut cancel = cancel_rx.clone();
                    tokio::select! {
                        _ = tokio::time::sleep(backoff(attempt, task.url.len())) => {}
                        _ = wait_cancelled(&mut cancel) => return Err(LaunchError::Cancelled),
                    }
                }
            }
        }
    }
    Err(last_error
        .unwrap_or_else(|| LaunchError::Network(format!("failed to download {}", task.url))))
}

pub async fn retain_missing(tasks: Vec<DownloadTask>, deep: bool) -> Vec<DownloadTask> {
    stream::iter(tasks.into_iter().map(|task| async move {
        if file_is_valid(&task.path, task.sha1.as_deref(), task.size, deep).await {
            None
        } else {
            Some(task)
        }
    }))
    .buffer_unordered(VALIDATION_CONCURRENCY)
    .filter_map(|task| async move { task })
    .collect()
    .await
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

    let first_pass = stream::iter(tasks.into_iter().map(|task| {
        let downloaded = downloaded.clone();
        let finished = finished.clone();
        let cancel_rx = cancel_rx.clone();
        let client = client.clone();
        async move {
            let result = download(&client, &task, &downloaded, &cancel_rx).await;
            if result.is_ok() {
                finished.fetch_add(1, Ordering::Relaxed);
            }
            (task, result)
        }
    }))
    .buffer_unordered(concurrency.max(1))
    .collect::<Vec<_>>()
    .await;

    let mut results = Vec::with_capacity(first_pass.len());
    let mut retry = Vec::new();
    for (task, result) in first_pass {
        match result {
            Err(LaunchError::Network(_)) | Err(LaunchError::Integrity(_))
                if !*cancel_rx.borrow() =>
            {
                retry.push(task)
            }
            other => results.push(other),
        }
    }
    if !retry.is_empty() {
        let second_pass = stream::iter(retry.into_iter().map(|task| {
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
        .buffer_unordered(RETRY_PASS_CONCURRENCY)
        .collect::<Vec<_>>()
        .await;
        results.extend(second_pass);
    }

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
            "{} of {} file(s) could not be downloaded. Check your internet connection and press Play again; finished files are kept and the download continues where it stopped. Details: {}",
            failures.len(),
            total_files,
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
