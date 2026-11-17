use tauri::AppHandle;

#[derive(Debug, serde::Serialize)]
pub struct AppInfo {
    pub name: String,
    pub version: String,
}

#[tauri::command]
pub fn get_app_info(app: AppHandle) -> AppInfo {
    let package_info = app.package_info();

    let name = env!("CARGO_PKG_NAME");
    let workspace_name = if name == "pretupid" {
        "Pretupid Launcher".to_string()
    } else {
        package_info.name.clone()
    };

    AppInfo {
        name: workspace_name,
        version: env!("CARGO_PKG_VERSION").to_string(),
    }
}

const EXTERNAL_HOSTS: [&str; 3] = ["pretupid.yannosay.com", "github.com", "modrinth.com"];

#[tauri::command]
pub fn open_external_link(url: String) -> Result<(), String> {
    let parsed = url::Url::parse(&url).map_err(|_| "Invalid link.".to_string())?;
    let host = parsed.host_str().unwrap_or_default();
    if parsed.scheme() != "https" || !EXTERNAL_HOSTS.contains(&host) {
        return Err("This link cannot be opened.".to_string());
    }
    let program = if cfg!(target_os = "windows") {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(program)
        .arg(parsed.as_str())
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("Could not open the link: {e}"))
}

const NEWS_ENDPOINT: &str = "https://pretupid.yannosay.com/api/news";
const MAX_NEWS_POSTS: usize = 6;

#[derive(Debug, serde::Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsPost {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub summary: String,
    #[serde(default)]
    pub category: String,
    #[serde(default)]
    pub pinned: bool,
    #[serde(default)]
    pub published_at: Option<i64>,
}

#[derive(Debug, serde::Deserialize)]
struct NewsResponse {
    posts: Vec<NewsPost>,
}

#[tauri::command]
pub async fn fetch_news() -> Result<Vec<NewsPost>, String> {
    let client = reqwest::Client::builder()
        .user_agent(super::util::USER_AGENT)
        .connect_timeout(std::time::Duration::from_secs(6))
        .timeout(std::time::Duration::from_secs(10))
        .build()
        .map_err(|e| e.to_string())?;
    let response = client
        .get(NEWS_ENDPOINT)
        .send()
        .await
        .map_err(|_| "News are unavailable right now.".to_string())?;
    if !response.status().is_success() {
        return Err("News are unavailable right now.".to_string());
    }
    let mut body: NewsResponse = response
        .json()
        .await
        .map_err(|_| "News could not be read.".to_string())?;
    body.posts.truncate(MAX_NEWS_POSTS);
    for post in &mut body.posts {
        post.title = post.title.chars().take(120).collect();
        post.summary = post.summary.chars().take(280).collect();
    }
    Ok(body.posts)
}

#[derive(Debug, serde::Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsArticle {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub summary: String,
    #[serde(default)]
    pub category: String,
    #[serde(default)]
    pub published_at: Option<i64>,
    #[serde(default)]
    pub body: String,
}

const MAX_NEWS_BODY_CHARS: usize = 60_000;

fn is_news_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 64
        && id
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-')
}

#[tauri::command]
pub async fn fetch_news_post(id: String) -> Result<NewsArticle, String> {
    if !is_news_id(&id) {
        return Err("This post does not exist.".to_string());
    }
    let client = reqwest::Client::builder()
        .user_agent(super::util::USER_AGENT)
        .connect_timeout(std::time::Duration::from_secs(6))
        .timeout(std::time::Duration::from_secs(10))
        .build()
        .map_err(|e| e.to_string())?;
    let response = client
        .get(format!("{NEWS_ENDPOINT}/{id}"))
        .send()
        .await
        .map_err(|_| "This post is unavailable right now.".to_string())?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Err("This post was removed.".to_string());
    }
    if !response.status().is_success() {
        return Err("This post is unavailable right now.".to_string());
    }
    let mut article: NewsArticle = response
        .json()
        .await
        .map_err(|_| "This post could not be read.".to_string())?;
    article.title = article.title.chars().take(120).collect();
    article.body = article.body.chars().take(MAX_NEWS_BODY_CHARS).collect();
    Ok(article)
}

#[cfg(test)]
mod tests {
    use super::is_news_id;

    #[test]
    fn news_ids_are_slugs() {
        assert!(is_news_id("pretupid-0-1-3-a1b2c3"));
        assert!(!is_news_id(""));
        assert!(!is_news_id("../admin"));
        assert!(!is_news_id("Upper"));
        assert!(!is_news_id(&"a".repeat(65)));
    }
}
