use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use rand::Rng;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use sqlx::SqlitePool;
use std::sync::Mutex;
use url::Url;
use uuid::Uuid;

use crate::error::{CoreError, CoreResult};
use super::models::{AuthTokens, MinecraftProfile};

const MICROSOFT_AUTH_URL: &str = "https://login.live.com/oauth20_authorize.srf";
const MICROSOFT_TOKEN_URL: &str = "https://login.live.com/oauth20_token.srf";
const XBOX_LIVE_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MINECRAFT_AUTH_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MINECRAFT_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

const CLIENT_ID: &str = "00000000402b5328";
const REDIRECT_URI: &str = "https://login.live.com/oauth20_desktop.srf";

static PENDING_VERIFIER: Mutex<Option<String>> = Mutex::new(None);
static PENDING_STATE: Mutex<Option<String>> = Mutex::new(None);

#[derive(Debug, Serialize)]
struct MicrosoftTokenRequest {
    client_id: String,
    code: String,
    grant_type: String,
    redirect_uri: String,
    code_verifier: String,
}

#[derive(Debug, Deserialize)]
struct MicrosoftTokenResponse {
    access_token: String,
    refresh_token: String,
}

#[derive(Debug, Serialize)]
struct XboxLiveRequest {
    #[serde(rename = "Properties")]
    properties: XboxLiveProperties,
    #[serde(rename = "RelyingParty")]
    relying_party: String,
    #[serde(rename = "TokenType")]
    token_type: String,
}

#[derive(Debug, Serialize)]
struct XboxLiveProperties {
    #[serde(rename = "AuthMethod")]
    auth_method: String,
    #[serde(rename = "SiteName")]
    site_name: String,
    #[serde(rename = "RpsTicket")]
    rps_ticket: String,
}

#[derive(Debug, Deserialize)]
struct XboxLiveResponse {
    #[serde(rename = "Token")]
    token: String,
}

#[derive(Debug, Serialize)]
struct XstsRequest {
    #[serde(rename = "Properties")]
    properties: XstsProperties,
    #[serde(rename = "RelyingParty")]
    relying_party: String,
    #[serde(rename = "TokenType")]
    token_type: String,
}

#[derive(Debug, Serialize)]
struct XstsProperties {
    #[serde(rename = "SandboxId")]
    sandbox_id: String,
    #[serde(rename = "UserTokens")]
    user_tokens: Vec<String>,
}

#[derive(Debug, Deserialize)]
struct XstsResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: XstsDisplayClaims,
}

#[derive(Debug, Deserialize)]
struct XstsDisplayClaims {
    xui: Vec<XstsXui>,
}

#[derive(Debug, Deserialize)]
struct XstsXui {
    #[serde(rename = "uhs")]
    uhs: String,
}

#[derive(Debug, Serialize)]
struct MinecraftAuthRequest {
    #[serde(rename = "identityToken")]
    identity_token: String,
}

#[derive(Debug, Deserialize)]
struct MinecraftAuthResponse {
    #[serde(rename = "access_token")]
    access_token: String,
}

#[derive(Debug, Deserialize)]
struct MinecraftProfileResponse {
    id: String,
    name: String,
}

#[derive(Debug, Serialize)]
struct RefreshTokenRequest {
    client_id: String,
    grant_type: String,
    refresh_token: String,
    redirect_uri: String,
}

fn generate_code_verifier() -> String {
    let random_bytes: Vec<u8> = (0..64).map(|_| rand::rng().random::<u8>()).collect();
    URL_SAFE_NO_PAD.encode(random_bytes)
}

fn generate_code_challenge(verifier: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(verifier.as_bytes());
    URL_SAFE_NO_PAD.encode(hasher.finalize())
}

fn generate_state() -> String {
    Uuid::new_v4().to_string()
}

pub struct OAuthClient {
    http: Client,
    pool: SqlitePool,
}

impl OAuthClient {
    pub fn new(pool: SqlitePool) -> Self {
        Self {
            http: Client::new(),
            pool,
        }
    }

    async fn ensure_tables(&self) -> CoreResult<()> {
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS auth_tokens (access_token TEXT NOT NULL, refresh_token TEXT NOT NULL, expires_at INTEGER NOT NULL)",
        )
        .execute(&self.pool)
        .await?;

        sqlx::query(
            "CREATE TABLE IF NOT EXISTS auth_profile (id TEXT PRIMARY KEY, name TEXT NOT NULL)",
        )
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    pub async fn prepare_login(&self) -> CoreResult<String> {
        let code_verifier = generate_code_verifier();
        let code_challenge = generate_code_challenge(&code_verifier);
        let state = generate_state();

        {
            let mut pending_verifier = PENDING_VERIFIER.lock().unwrap();
            *pending_verifier = Some(code_verifier);
        }
        {
            let mut pending_state = PENDING_STATE.lock().unwrap();
            *pending_state = Some(state.clone());
        }

        Url::parse_with_params(
            MICROSOFT_AUTH_URL,
            &[
                ("client_id", CLIENT_ID),
                ("response_type", "code"),
                ("redirect_uri", REDIRECT_URI),
                ("scope", "XboxLive.signin offline_access"),
                ("code_challenge", &code_challenge),
                ("code_challenge_method", "S256"),
                ("state", &state),
                ("prompt", "select_account"),
            ],
        )
        .map(|url| url.to_string())
        .map_err(|e| CoreError::InvalidInput(format!("failed to build auth URL: {e}")))
    }

    pub async fn handle_callback(&self, code: &str, state: &str) -> CoreResult<AuthTokens> {
        let expected_state = {
            let mut pending_state = PENDING_STATE.lock().unwrap();
            pending_state.take()
        };

        if expected_state.is_none() {
            return Err(CoreError::Storage("no pending OAuth flow".to_string()));
        }

        if expected_state.as_deref() != Some(state) {
            return Err(CoreError::Storage("OAuth state mismatch".to_string()));
        }

        let code_verifier = {
            let mut pending_verifier = PENDING_VERIFIER.lock().unwrap();
            pending_verifier.take()
        }
        .ok_or_else(|| CoreError::Storage("no pending OAuth flow".to_string()))?;

        let microsoft_tokens = self
            .exchange_code(code, &code_verifier)
            .await?;

        let (minecraft_token, minecraft_profile) = self
            .authenticate_minecraft(&microsoft_tokens.access_token)
            .await?;

        let auth_tokens = AuthTokens {
            access_token: minecraft_token,
            refresh_token: microsoft_tokens.refresh_token,
            expires_at: chrono::Utc::now().timestamp() + 82800,
        };

        self.ensure_tables().await?;
        self.save_profile_to_db(&minecraft_profile).await?;
        self.save_tokens_to_db(&auth_tokens).await?;

        Ok(auth_tokens)
    }

    async fn save_profile_to_db(&self, profile: &MinecraftProfile) -> CoreResult<()> {
        self.ensure_tables().await?;

        sqlx::query("INSERT OR REPLACE INTO auth_profile (id, name) VALUES (?, ?)")
            .bind(&profile.id)
            .bind(&profile.name)
            .execute(&self.pool)
            .await?;

        Ok(())
    }

    pub async fn get_tokens(&self) -> CoreResult<AuthTokens> {
        self.ensure_tables().await?;
        let stored = self.load_tokens_from_db().await?.ok_or_else(|| {
            CoreError::NotFound("No Microsoft account logged in".to_string())
        })?;

        if chrono::Utc::now().timestamp() >= stored.expires_at {
            self.refresh().await
        } else {
            Ok(stored)
        }
    }

    async fn save_tokens_to_db(&self, tokens: &AuthTokens) -> CoreResult<()> {
        self.ensure_tables().await?;
        sqlx::query("DELETE FROM auth_tokens")
            .execute(&self.pool)
            .await?;

        sqlx::query(
            "INSERT INTO auth_tokens (access_token, refresh_token, expires_at) VALUES (?, ?, ?)",
        )
        .bind(&tokens.access_token)
        .bind(&tokens.refresh_token)
        .bind(tokens.expires_at)
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    async fn load_tokens_from_db(&self) -> CoreResult<Option<AuthTokens>> {
        let row = sqlx::query_as::<_, (String, String, i64)>(
            "SELECT access_token, refresh_token, expires_at FROM auth_tokens LIMIT 1",
        )
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(|(access_token, refresh_token, expires_at)| AuthTokens {
            access_token,
            refresh_token,
            expires_at,
        }))
    }

    async fn delete_tokens_from_db(&self) -> CoreResult<()> {
        sqlx::query("DELETE FROM auth_tokens")
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    async fn delete_profile_from_db(&self) -> CoreResult<()> {
        sqlx::query("DELETE FROM auth_profile")
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    pub async fn get_profile(&self) -> CoreResult<Option<MinecraftProfile>> {
        self.ensure_tables().await?;
        let row = sqlx::query_as::<_, (String, String)>(
            "SELECT id, name FROM auth_profile LIMIT 1",
        )
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(|(id, name)| MinecraftProfile { id, name }))
    }

    pub async fn get_status(&self) -> CoreResult<super::models::AuthStatus> {
        self.ensure_tables().await?;
        let row = sqlx::query_as::<_, (String, String)>(
            "SELECT id, name FROM auth_profile LIMIT 1",
        )
        .fetch_optional(&self.pool)
        .await?;

        Ok(super::models::AuthStatus {
            authenticated: row.is_some(),
            profile: row.map(|(id, name)| MinecraftProfile { id, name }),
        })
    }

    pub async fn refresh(&self) -> CoreResult<AuthTokens> {
        self.ensure_tables().await?;
        let stored = self.load_tokens_from_db().await?.ok_or_else(|| {
            CoreError::NotFound("no stored tokens".to_string())
        })?;

        let response = self
            .http
            .post(MICROSOFT_TOKEN_URL)
            .form(&RefreshTokenRequest {
                client_id: CLIENT_ID.to_string(),
                grant_type: "refresh_token".to_string(),
                refresh_token: stored.refresh_token.clone(),
                redirect_uri: REDIRECT_URI.to_string(),
            })
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("token refresh failed: {e}")))?;

        if !response.status().is_success() {
            let status = response.status();
            let text = response.text().await.unwrap_or_default();
            return Err(CoreError::Storage(format!(
                "token refresh failed with status {status}: {text}"
            )));
        }

        let parsed: MicrosoftTokenResponse = response
            .json()
            .await
            .map_err(|e| CoreError::Storage(format!("failed to parse token response: {e}")))?;

        let (minecraft_token, minecraft_profile) = self
            .authenticate_minecraft(&parsed.access_token)
            .await?;

        let tokens = AuthTokens {
            access_token: minecraft_token,
            refresh_token: parsed.refresh_token,
            expires_at: chrono::Utc::now().timestamp() + 82800,
        };

        self.save_tokens_to_db(&tokens).await?;
        self.save_profile_to_db(&minecraft_profile).await?;

        Ok(tokens)
    }

    pub async fn ensure_valid_token(&self) -> CoreResult<AuthTokens> {
        let tokens = self.get_tokens().await?;

        let response = self
            .http
            .get(MINECRAFT_PROFILE_URL)
            .bearer_auth(&tokens.access_token)
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("profile validation failed: {e}")))?;

        if response.status().is_success() {
            if self.get_profile().await?.is_none() {
                let profile = self.fetch_minecraft_profile(&tokens.access_token).await?;
                self.save_profile_to_db(&profile).await?;
            }
            return Ok(tokens);
        }

        if response.status().as_u16() == 401 || response.status().as_u16() == 403 {
            let refreshed = self.refresh().await?;
            return Ok(refreshed);
        }

        Err(CoreError::Storage(format!(
            "failed to validate Minecraft token: {}",
            response.status()
        )))
    }

    pub async fn logout(&self) -> CoreResult<()> {
        self.ensure_tables().await?;
        self.delete_tokens_from_db().await?;
        self.delete_profile_from_db().await?;
        Ok(())
    }

    async fn exchange_code(
        &self,
        code: &str,
        code_verifier: &str,
    ) -> CoreResult<MicrosoftTokenResponse> {
        let response = self
            .http
            .post(MICROSOFT_TOKEN_URL)
            .form(&MicrosoftTokenRequest {
                client_id: CLIENT_ID.to_string(),
                code: code.to_string(),
                grant_type: "authorization_code".to_string(),
                redirect_uri: REDIRECT_URI.to_string(),
                code_verifier: code_verifier.to_string(),
            })
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("token exchange failed: {e}")))?;

        if !response.status().is_success() {
            let status = response.status();
            let text = response.text().await.unwrap_or_default();
            return Err(CoreError::Storage(format!(
                "Microsoft token exchange failed with status {status}: {text}"
            )));
        }

        response
            .json::<MicrosoftTokenResponse>()
            .await
            .map_err(|e| CoreError::Storage(format!("failed to parse token response: {e}")))
    }

    async fn authenticate_minecraft(
        &self,
        microsoft_token: &str,
    ) -> CoreResult<(String, MinecraftProfile)> {
        let xbox_token = self.authenticate_xbox_live(microsoft_token).await?;
        let xsts_result = self.authenticate_xsts(&xbox_token).await?;
        let minecraft_token = self
            .authenticate_minecraft_with_xsts(&xsts_result.token, &xsts_result.uhs)
            .await?;
        let profile = self.fetch_minecraft_profile(&minecraft_token).await?;
        Ok((minecraft_token, profile))
    }

    async fn authenticate_xbox_live(&self, access_token: &str) -> CoreResult<String> {
        let response = self
            .http
            .post(XBOX_LIVE_AUTH_URL)
            .json(&XboxLiveRequest {
                properties: XboxLiveProperties {
                    auth_method: "RPS".to_string(),
                    site_name: "user.auth.xboxlive.com".to_string(),
                    rps_ticket: format!("d={access_token}"),
                },
                relying_party: "http://auth.xboxlive.com".to_string(),
                token_type: "JWT".to_string(),
            })
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("Xbox Live auth failed: {e}")))?;

        if !response.status().is_success() {
            let status = response.status();
            let text = response.text().await.unwrap_or_default();
            return Err(CoreError::Storage(format!(
                "Xbox Live auth failed with status {status}: {text}"
            )));
        }

        response
            .json::<XboxLiveResponse>()
            .await
            .map(|r| r.token)
            .map_err(|e| CoreError::Storage(format!("failed to parse Xbox Live response: {e}")))
    }

    async fn authenticate_xsts(&self, xbox_token: &str) -> CoreResult<XstsAuthResult> {
        let response = self
            .http
            .post(XSTS_AUTH_URL)
            .json(&XstsRequest {
                properties: XstsProperties {
                    sandbox_id: "RETAIL".to_string(),
                    user_tokens: vec![xbox_token.to_string()],
                },
                relying_party: "rp://api.minecraftservices.com/".to_string(),
                token_type: "JWT".to_string(),
            })
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("XSTS auth failed: {e}")))?;

        if !response.status().is_success() {
            let status = response.status();
            let text = response.text().await.unwrap_or_default();
            return Err(CoreError::Storage(format!(
                "XSTS auth failed with status {status}: {text}"
            )));
        }

        let parsed: XstsResponse = response
            .json()
            .await
            .map_err(|e| CoreError::Storage(format!("failed to parse XSTS response: {e}")))?;

        let uhs = parsed
            .display_claims
            .xui
            .first()
            .map(|x| x.uhs.clone())
            .ok_or_else(|| CoreError::Storage("no uhs in XSTS response".to_string()))?;

        Ok(XstsAuthResult {
            token: parsed.token,
            uhs,
        })
    }

    async fn authenticate_minecraft_with_xsts(
        &self,
        xsts_token: &str,
        uhs: &str,
    ) -> CoreResult<String> {
        let identity_token = format!("XBL3.0 x={uhs};{xsts_token}");

        let response = self
            .http
            .post(MINECRAFT_AUTH_URL)
            .json(&MinecraftAuthRequest { identity_token })
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("Minecraft auth failed: {e}")))?;

        if !response.status().is_success() {
            let status = response.status();
            let text = response.text().await.unwrap_or_default();
            return Err(CoreError::Storage(format!(
                "Minecraft auth failed with status {status}: {text}"
            )));
        }

        response
            .json::<MinecraftAuthResponse>()
            .await
            .map(|r| r.access_token)
            .map_err(|e| CoreError::Storage(format!("failed to parse Minecraft auth response: {e}")))
    }

    async fn fetch_minecraft_profile(&self, minecraft_token: &str) -> CoreResult<MinecraftProfile> {
        let response = self
            .http
            .get(MINECRAFT_PROFILE_URL)
            .bearer_auth(minecraft_token)
            .send()
            .await
            .map_err(|e| CoreError::Storage(format!("profile fetch failed: {e}")))?;

        if !response.status().is_success() {
            let status = response.status();
            let text = response.text().await.unwrap_or_default();
            return Err(CoreError::Storage(format!(
                "profile fetch failed with status {status}: {text}"
            )));
        }

        response
            .json::<MinecraftProfileResponse>()
            .await
            .map(|r| MinecraftProfile {
                id: r.id,
                name: r.name,
            })
            .map_err(|e| CoreError::Storage(format!("failed to parse profile response: {e}")))
    }
}

struct XstsAuthResult {
    token: String,
    uhs: String,
}