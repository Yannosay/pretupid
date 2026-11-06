use std::path::{Path, PathBuf};

use futures_util::StreamExt;
use sha2::{Digest, Sha256};
use tokio::io::AsyncWriteExt;
use tokio::sync::mpsc;
use tokio::sync::watch;

use crate::error::LaunchError;
use crate::launcher::LaunchEvent;
use crate::process;
use crate::LaunchResult;

const JAVA_EXE: &str = if cfg!(windows) { "java.exe" } else { "java" };

pub struct JavaRuntime;

impl JavaRuntime {
    pub async fn detect_for_version(
        major_version: u32,
        auto_install: bool,
        java_dir: PathBuf,
        event_tx: mpsc::UnboundedSender<LaunchEvent>,
        mut cancel_rx: watch::Receiver<bool>,
    ) -> LaunchResult<PathBuf> {
        event_tx
            .send(LaunchEvent::Log {
                line: format!("Detecting Java {}...", major_version),
            })
            .ok();

        if let Some(path) = Self::find_java_in_dir(&java_dir, major_version) {
            event_tx
                .send(LaunchEvent::Log {
                    line: format!("Using cached Java: {}", path.display()),
                })
                .ok();
            return Ok(path);
        }

        if let Some(path) = Self::find_installed_java(major_version) {
            event_tx
                .send(LaunchEvent::Log {
                    line: format!("Using installed Java: {}", path.display()),
                })
                .ok();
            return Ok(path);
        }

        if !auto_install {
            return Err(LaunchError::NotFound(format!(
                "Java {} is required but auto-install is disabled. Please install it manually or set a custom Java path.",
                major_version
            )));
        }

        event_tx
            .send(LaunchEvent::Progress {
                current: 0,
                total: 0,
                message: format!("Installing Java {}...", major_version),
            })
            .ok();

        Self::install_java(major_version, &java_dir, &event_tx, &mut cancel_rx).await?;

        if let Some(path) = Self::find_java_in_dir(&java_dir, major_version) {
            event_tx
                .send(LaunchEvent::Log {
                    line: format!("Java installed: {}", path.display()),
                })
                .ok();
            return Ok(path);
        }

        Err(LaunchError::NotFound(format!(
            "Failed to locate Java {} after installation.",
            major_version
        )))
    }

    fn find_installed_java(major_version: u32) -> Option<PathBuf> {
        let mut candidates = Vec::new();

        if let Ok(java_home) = std::env::var("JAVA_HOME") {
            let exe = PathBuf::from(&java_home).join("bin").join(JAVA_EXE);
            if exe.exists() {
                candidates.push(exe);
            }
        }

        if let Ok(output) = process::std_command(if cfg!(windows) { "where" } else { "which" })
            .arg("java")
            .output()
        {
            if output.status.success() {
                let stdout = String::from_utf8_lossy(&output.stdout);
                for line in stdout.lines() {
                    let p = PathBuf::from(line.trim());
                    if p.exists() {
                        candidates.push(p);
                    }
                }
            }
        }

        let roots = [
            "C:\\Program Files\\Java",
            "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft",
            "C:\\Program Files\\Zulu",
        ];

        for root in roots {
            if let Ok(entries) = std::fs::read_dir(root) {
                for entry in entries.flatten() {
                    let exe = entry.path().join("bin").join(JAVA_EXE);
                    if exe.exists() {
                        candidates.push(exe);
                    }
                }
            }
        }

        candidates.dedup();

        candidates
            .into_iter()
            .find(|candidate| Self::java_matches_major(candidate, major_version))
    }

    pub fn java_major(java_exe: &Path) -> Option<u32> {
        let output = process::std_command(java_exe)
            .arg("-version")
            .output()
            .ok()?;
        let version_text = String::from_utf8_lossy(&output.stderr).to_string();
        Self::parse_java_major(&version_text)
    }

    fn java_matches_major(java_exe: &Path, major_version: u32) -> bool {
        Self::java_major(java_exe) == Some(major_version)
    }

    fn parse_java_major(version_text: &str) -> Option<u32> {
        let first_line = version_text.lines().next().unwrap_or("");
        let quoted = first_line.split('"').nth(1).unwrap_or(first_line);

        if quoted.starts_with("1.8") {
            return Some(8);
        }

        let first_num = quoted.split('.').next().unwrap_or("");
        first_num.parse::<u32>().ok()
    }

    async fn install_java(
        major_version: u32,
        java_dir: &Path,
        event_tx: &mpsc::UnboundedSender<LaunchEvent>,
        cancel_rx: &mut watch::Receiver<bool>,
    ) -> LaunchResult<()> {
        std::fs::create_dir_all(java_dir).map_err(|e| LaunchError::Io(e.to_string()))?;

        let os = if cfg!(target_os = "windows") {
            "windows"
        } else if cfg!(target_os = "macos") {
            "mac"
        } else {
            "linux"
        };

        let arch = if cfg!(target_arch = "x86_64") {
            "x64"
        } else if cfg!(target_arch = "aarch64") {
            "aarch64"
        } else {
            "x64"
        };

        let api_url = format!(
            "https://api.adoptium.net/v3/assets/latest/{}/hotspot?architecture={}&image_type=jre&os={}",
            major_version, arch, os
        );

        let response = crate::download::http_client()?
            .get(&api_url)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?
            .json::<serde_json::Value>()
            .await
            .map_err(|e| LaunchError::Parse(e.to_string()))?;

        let assets = response
            .as_array()
            .ok_or_else(|| LaunchError::Parse("invalid Adoptium API response".to_string()))?;

        let mut download_url: Option<String> = None;
        let mut package_name: Option<String> = None;
        let mut package_checksum: Option<String> = None;

        for asset in assets {
            let binary = asset.get("binary").and_then(|b| b.as_object());
            if let Some(binary) = binary {
                if binary.get("image_type").and_then(|v| v.as_str()) == Some("jre") {
                    if let Some(package) = binary.get("package").and_then(|p| p.as_object()) {
                        if let Some(link) = package.get("link").and_then(|v| v.as_str()) {
                            if let Some(name) = package.get("name").and_then(|v| v.as_str()) {
                                download_url = Some(link.to_string());
                                package_name = Some(name.to_string());
                                package_checksum = package
                                    .get("checksum")
                                    .and_then(|v| v.as_str())
                                    .map(|v| v.to_lowercase());
                                break;
                            }
                        }
                    }
                }
            }
        }

        let download_url = download_url
            .ok_or_else(|| LaunchError::NotFound("no compatible Java runtime found".to_string()))?;
        let package_name = package_name.unwrap_or_else(|| "java.zip".to_string());
        if package_name.contains('/') || package_name.contains('\\') || package_name.contains("..")
        {
            return Err(LaunchError::Parse(
                "unexpected Java package name".to_string(),
            ));
        }

        event_tx
            .send(LaunchEvent::Log {
                line: format!("Downloading Java {} from Adoptium...", major_version),
            })
            .ok();

        let response = crate::download::http_client()?
            .get(&download_url)
            .send()
            .await
            .map_err(|e| LaunchError::Network(e.to_string()))?;

        if !response.status().is_success() {
            return Err(LaunchError::Network(format!(
                "Java download failed with status {}",
                response.status()
            )));
        }

        let total_size = response.content_length().unwrap_or(0);
        let archive_path = java_dir.join(&package_name);

        let mut file = tokio::fs::File::create(&archive_path)
            .await
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        let mut stream = response.bytes_stream();
        let mut downloaded: u64 = 0;
        let mut hasher = Sha256::new();

        loop {
            tokio::select! {
                _ = cancel_rx.changed() => {
                    if *cancel_rx.borrow() {
                        let _ = tokio::fs::remove_file(&archive_path).await;
                        return Err(LaunchError::Cancelled);
                    }
                }
                chunk = stream.next() => {
                    match chunk {
                        Some(Ok(bytes)) => {
                            downloaded += bytes.len() as u64;
                            hasher.update(&bytes);
                            file.write_all(&bytes)
                                .await
                                .map_err(|e| LaunchError::Io(e.to_string()))?;

                            if total_size > 0 {
                                event_tx
                                    .send(LaunchEvent::Progress {
                                        current: downloaded,
                                        total: total_size,
                                        message: format!("Installing Java {}...", major_version),
                                    })
                                    .ok();
                            }
                        }
                        Some(Err(e)) => {
                            return Err(LaunchError::Network(e.to_string()));
                        }
                        None => break,
                    }
                }
            }
        }

        drop(file);

        if total_size > 0 && downloaded != total_size {
            let _ = tokio::fs::remove_file(&archive_path).await;
            return Err(LaunchError::Io(format!(
                "incomplete Java download: expected {} bytes, got {}",
                total_size, downloaded
            )));
        }

        if let Some(expected) = package_checksum {
            let actual = format!("{:x}", hasher.finalize());
            if actual != expected {
                let _ = tokio::fs::remove_file(&archive_path).await;
                return Err(LaunchError::Integrity(format!(
                    "Java download checksum mismatch (expected {}, got {})",
                    expected, actual
                )));
            }
        }

        if package_name.ends_with(".zip") {
            event_tx
                .send(LaunchEvent::Log {
                    line: "Extracting Java...".to_string(),
                })
                .ok();
            Self::extract_zip(&archive_path, java_dir)?;
        } else {
            let _ = tokio::fs::remove_file(&archive_path).await;
            return Err(LaunchError::Parse(format!(
                "unsupported Java package format: {}",
                package_name
            )));
        }

        let _ = tokio::fs::remove_file(&archive_path).await;
        Ok(())
    }

    fn extract_zip(archive_path: &Path, destination: &Path) -> LaunchResult<()> {
        let file = std::fs::File::open(archive_path).map_err(|e| LaunchError::Io(e.to_string()))?;

        let mut archive = zip::ZipArchive::new(file)
            .map_err(|e| LaunchError::Parse(format!("invalid zip archive: {}", e)))?;

        for i in 0..archive.len() {
            let mut file = archive
                .by_index(i)
                .map_err(|e| LaunchError::Parse(e.to_string()))?;

            let out_path = match file.enclosed_name() {
                Some(path) => destination.join(path),
                None => continue,
            };

            if file.is_dir() {
                std::fs::create_dir_all(&out_path).map_err(|e| LaunchError::Io(e.to_string()))?;
                continue;
            }

            if let Some(parent) = out_path.parent() {
                std::fs::create_dir_all(parent).map_err(|e| LaunchError::Io(e.to_string()))?;
            }

            let mut out_file =
                std::fs::File::create(&out_path).map_err(|e| LaunchError::Io(e.to_string()))?;

            std::io::copy(&mut file, &mut out_file).map_err(|e| LaunchError::Io(e.to_string()))?;
        }

        Ok(())
    }

    fn find_java_in_dir(java_dir: &Path, major_version: u32) -> Option<PathBuf> {
        let mut stack = vec![java_dir.to_path_buf()];

        while let Some(dir) = stack.pop() {
            let entries = std::fs::read_dir(&dir).ok()?;
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    stack.push(path);
                    continue;
                }

                if path.file_name().and_then(|n| n.to_str()) == Some(JAVA_EXE)
                    && Self::java_matches_major(&path, major_version)
                {
                    return Some(path);
                }
            }
        }

        None
    }

    pub fn detect() -> Result<PathBuf, LaunchError> {
        let java_home = std::env::var("JAVA_HOME").ok().map(PathBuf::from);

        if let Some(home) = java_home {
            let exe = home.join("bin").join(JAVA_EXE);
            if exe.exists() {
                return Ok(exe);
            }
        }

        let output = process::std_command(if cfg!(windows) { "where" } else { "which" })
            .arg("java")
            .output()
            .map_err(|e| LaunchError::Io(e.to_string()))?;

        if output.status.success() {
            let stdout = String::from_utf8_lossy(&output.stdout);
            let first = stdout
                .lines()
                .next()
                .ok_or_else(|| LaunchError::NotFound("java not found".to_string()))?;
            return Ok(PathBuf::from(first.trim()));
        }

        Err(LaunchError::NotFound(
            "Java runtime not found. Install a compatible Java version or enable auto-install."
                .to_string(),
        ))
    }
}
