use std::io::Read;
use std::path::{Component, Path, PathBuf};

use futures_util::StreamExt;
use sha1::{Digest, Sha1};
use sha2::Sha512;
use tokio::io::AsyncWriteExt;

pub const USER_AGENT: &str = concat!(
    "pretupid-launcher/",
    env!("CARGO_PKG_VERSION"),
    " (github.com/Yannosay/pretupid)"
);
pub const MAX_DOWNLOAD_BYTES: u64 = 1024 * 1024 * 1024;

pub fn http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(std::time::Duration::from_secs(15))
        .read_timeout(std::time::Duration::from_secs(25))
        .timeout(std::time::Duration::from_secs(900))
        .pool_max_idle_per_host(32)
        .tcp_keepalive(std::time::Duration::from_secs(30))
        .redirect(reqwest::redirect::Policy::limited(8))
        .build()
        .map_err(|e| e.to_string())
}

pub fn safe_relative_join(base: &Path, relative: &str) -> Option<PathBuf> {
    let normalized = relative.replace('\\', "/");
    if normalized.is_empty() || normalized.starts_with('/') || normalized.contains(':') {
        return None;
    }
    let candidate = Path::new(&normalized);
    let mut out = base.to_path_buf();
    for component in candidate.components() {
        match component {
            Component::Normal(part) => {
                let text = part.to_string_lossy();
                if text.is_empty() || text.ends_with('.') || text.ends_with(' ') {
                    return None;
                }
                out.push(part);
            }
            Component::CurDir => {}
            _ => return None,
        }
    }
    if out == base {
        return None;
    }
    Some(out)
}

pub fn is_allowed_download_url(url: &str) -> bool {
    match url::Url::parse(url) {
        Ok(parsed) => parsed.scheme() == "https" && parsed.host_str().is_some(),
        Err(_) => false,
    }
}

pub fn is_modrinth_pack_host(url: &str) -> bool {
    const ALLOWED: &[&str] = &[
        "cdn.modrinth.com",
        "github.com",
        "raw.githubusercontent.com",
        "gitlab.com",
        "objects.githubusercontent.com",
    ];
    match url::Url::parse(url) {
        Ok(parsed) => {
            parsed.scheme() == "https"
                && parsed
                    .host_str()
                    .map(|h| ALLOWED.iter().any(|a| h.eq_ignore_ascii_case(a)))
                    .unwrap_or(false)
        }
        Err(_) => false,
    }
}

pub async fn sha1_file(path: &Path) -> Result<String, String> {
    let path = path.to_path_buf();
    tokio::task::spawn_blocking(move || {
        let mut file = std::fs::File::open(&path).map_err(|e| e.to_string())?;
        let mut hasher = Sha1::new();
        std::io::copy(&mut file, &mut hasher).map_err(|e| e.to_string())?;
        Ok(format!("{:x}", hasher.finalize()))
    })
    .await
    .map_err(|e| e.to_string())?
}

pub async fn hashes_of_file(path: &Path) -> Result<(String, String, u64), String> {
    let path = path.to_path_buf();
    tokio::task::spawn_blocking(move || {
        let mut file = std::fs::File::open(&path).map_err(|e| e.to_string())?;
        let mut sha1 = Sha1::new();
        let mut sha512 = Sha512::new();
        let mut buffer = vec![0u8; 64 * 1024];
        let mut size = 0u64;
        loop {
            let read = file.read(&mut buffer).map_err(|e| e.to_string())?;
            if read == 0 {
                break;
            }
            sha1.update(&buffer[..read]);
            sha512.update(&buffer[..read]);
            size += read as u64;
        }
        Ok((
            format!("{:x}", sha1.finalize()),
            format!("{:x}", sha512.finalize()),
            size,
        ))
    })
    .await
    .map_err(|e| e.to_string())?
}

pub struct ExpectedHash<'a> {
    pub sha1: Option<&'a str>,
    pub sha512: Option<&'a str>,
}

impl ExpectedHash<'_> {
    pub const NONE: ExpectedHash<'static> = ExpectedHash {
        sha1: None,
        sha512: None,
    };
}

pub async fn download_verified(
    client: &reqwest::Client,
    url: &str,
    dest: &Path,
    expected: &ExpectedHash<'_>,
) -> Result<String, String> {
    if !is_allowed_download_url(url) {
        return Err(format!(
            "refusing to download from '{}' (only https is allowed)",
            url
        ));
    }
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent)
            .await
            .map_err(|e| e.to_string())?;
    }

    let response = client.get(url).send().await.map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        return Err(format!(
            "download failed with status {} for {}",
            response.status(),
            url
        ));
    }
    if response
        .content_length()
        .map(|l| l > MAX_DOWNLOAD_BYTES)
        .unwrap_or(false)
    {
        return Err("file is larger than the 1 GB safety limit".to_string());
    }

    let mut temp_name = dest
        .file_name()
        .map(|n| n.to_os_string())
        .unwrap_or_default();
    temp_name.push(".part");
    let temp = dest.with_file_name(temp_name);

    let mut file = tokio::fs::File::create(&temp)
        .await
        .map_err(|e| e.to_string())?;
    let mut sha1 = Sha1::new();
    let mut sha512 = Sha512::new();
    let mut written = 0u64;
    let mut stream = response.bytes_stream();
    let result: Result<(), String> = async {
        while let Some(chunk) = stream.next().await {
            let bytes = chunk.map_err(|e| e.to_string())?;
            written += bytes.len() as u64;
            if written > MAX_DOWNLOAD_BYTES {
                return Err("file is larger than the 1 GB safety limit".to_string());
            }
            sha1.update(&bytes);
            sha512.update(&bytes);
            file.write_all(&bytes).await.map_err(|e| e.to_string())?;
        }
        file.flush().await.map_err(|e| e.to_string())?;
        Ok(())
    }
    .await;
    drop(file);

    if let Err(err) = result {
        let _ = tokio::fs::remove_file(&temp).await;
        return Err(err);
    }

    let sha1_hex = format!("{:x}", sha1.finalize());
    let sha512_hex = format!("{:x}", sha512.finalize());
    let sha1_ok = expected
        .sha1
        .map(|e| e.eq_ignore_ascii_case(&sha1_hex))
        .unwrap_or(true);
    let sha512_ok = expected
        .sha512
        .map(|e| e.eq_ignore_ascii_case(&sha512_hex))
        .unwrap_or(true);
    if !sha1_ok || !sha512_ok {
        let _ = tokio::fs::remove_file(&temp).await;
        return Err(format!(
            "checksum mismatch for {}; the file was discarded",
            url
        ));
    }

    tokio::fs::rename(&temp, dest)
        .await
        .map_err(|e| e.to_string())?;
    Ok(sha1_hex)
}

pub fn copy_dir_recursive(src: &Path, dst: &Path, skip: &[&str]) -> Result<(), String> {
    std::fs::create_dir_all(dst).map_err(|e| e.to_string())?;
    for entry in std::fs::read_dir(src).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let name = entry.file_name();
        if skip
            .iter()
            .any(|s| name.to_string_lossy().eq_ignore_ascii_case(s))
        {
            continue;
        }
        let file_type = entry.file_type().map_err(|e| e.to_string())?;
        if file_type.is_symlink() {
            continue;
        }
        let target = dst.join(&name);
        if file_type.is_dir() {
            copy_dir_recursive(&entry.path(), &target, &[])?;
        } else {
            std::fs::copy(entry.path(), &target).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

pub fn extract_zip_safe(zip_path: &Path, dest: &Path, max_total_bytes: u64) -> Result<(), String> {
    let file = std::fs::File::open(zip_path).map_err(|e| e.to_string())?;
    let mut archive =
        zip::ZipArchive::new(file).map_err(|e| format!("not a valid archive: {e}"))?;
    let mut total = 0u64;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
        let rel = match entry.enclosed_name() {
            Some(p) => p,
            None => continue,
        };
        let out_path = match safe_relative_join(dest, &rel.to_string_lossy()) {
            Some(p) => p,
            None => continue,
        };
        if entry.is_dir() {
            std::fs::create_dir_all(&out_path).map_err(|e| e.to_string())?;
            continue;
        }
        total += entry.size();
        if total > max_total_bytes {
            return Err("archive expands beyond the safety limit".to_string());
        }
        if let Some(parent) = out_path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let mut out_file = std::fs::File::create(&out_path).map_err(|e| e.to_string())?;
        std::io::copy(&mut (&mut entry).take(max_total_bytes), &mut out_file)
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn open_in_file_manager(path: &Path) -> Result<(), String> {
    if !path.exists() {
        std::fs::create_dir_all(path).map_err(|e| e.to_string())?;
    }
    let program = if cfg!(target_os = "windows") {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(program)
        .arg(path)
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("could not open folder: {e}"))
}

pub fn unique_path(dir: &Path, file_name: &str) -> PathBuf {
    let candidate = dir.join(file_name);
    if !candidate.exists() {
        return candidate;
    }
    let path = Path::new(file_name);
    let stem = path
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|| file_name.to_string());
    let ext = path
        .extension()
        .map(|e| format!(".{}", e.to_string_lossy()))
        .unwrap_or_default();
    for index in 1..1000 {
        let candidate = dir.join(format!("{stem} ({index}){ext}"));
        if !candidate.exists() {
            return candidate;
        }
    }
    dir.join(format!("{stem}-{}{ext}", uuid::Uuid::new_v4()))
}

pub fn sanitize_file_name(name: &str) -> String {
    let cleaned: String = name
        .chars()
        .map(|c| {
            if c.is_control() || "/\\:*?\"<>|".contains(c) {
                '_'
            } else {
                c
            }
        })
        .collect();
    let trimmed = cleaned.trim().trim_end_matches('.').trim();
    if trimmed.is_empty() || trimmed == "." || trimmed == ".." {
        "file".to_string()
    } else {
        trimmed.chars().take(200).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn relative_join_blocks_traversal() {
        let base = Path::new("C:/base");
        assert!(safe_relative_join(base, "mods/a.jar").is_some());
        assert!(safe_relative_join(base, "../a.jar").is_none());
        assert!(safe_relative_join(base, "mods/../../a.jar").is_none());
        assert!(safe_relative_join(base, "C:/Windows/a.jar").is_none());
        assert!(safe_relative_join(base, "/etc/passwd").is_none());
        assert!(safe_relative_join(base, "mods\\..\\..\\x").is_none());
        assert!(safe_relative_join(base, "").is_none());
    }

    #[test]
    fn pack_hosts() {
        assert!(is_modrinth_pack_host(
            "https://cdn.modrinth.com/data/x/y.jar"
        ));
        assert!(!is_modrinth_pack_host(
            "http://cdn.modrinth.com/data/x/y.jar"
        ));
        assert!(!is_modrinth_pack_host("https://evil.example/x.jar"));
    }

    #[test]
    fn file_names() {
        assert_eq!(sanitize_file_name("../../a.jar"), ".._.._a.jar");
        assert_eq!(sanitize_file_name("..."), "file");
    }
}
