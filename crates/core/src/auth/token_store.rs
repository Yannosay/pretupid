use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use chacha20poly1305::aead::{Aead, AeadCore, KeyInit, OsRng};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use keyring::Entry;

use super::models::{AuthTokens, MinecraftProfile};
use crate::error::{CoreError, CoreResult};

const KEYRING_SERVICE: &str = "pretupid-launcher";
const KEYRING_SECRET_KEY: &str = "token-encryption-key";
const SEALED_PREFIX: &str = "enc:v1:";

fn encryption_key(create: bool) -> Option<Key> {
    let entry = Entry::new(KEYRING_SERVICE, KEYRING_SECRET_KEY).ok()?;
    match entry.get_password() {
        Ok(encoded) => {
            let bytes = STANDARD.decode(encoded.trim()).ok()?;
            if bytes.len() != 32 {
                return None;
            }
            Some(*Key::from_slice(&bytes))
        }
        Err(keyring::Error::NoEntry) if create => {
            let key = ChaCha20Poly1305::generate_key(&mut OsRng);
            entry.set_password(&STANDARD.encode(key)).ok()?;
            Some(key)
        }
        Err(_) => None,
    }
}

pub fn seal_secret(plain: &str) -> String {
    let key = match encryption_key(true) {
        Some(k) => k,
        None => {
            tracing::warn!("OS keyring unavailable; storing session token without encryption");
            return plain.to_string();
        }
    };
    let cipher = ChaCha20Poly1305::new(&key);
    let nonce = ChaCha20Poly1305::generate_nonce(&mut OsRng);
    match cipher.encrypt(&nonce, plain.as_bytes()) {
        Ok(ciphertext) => {
            let mut combined = nonce.to_vec();
            combined.extend_from_slice(&ciphertext);
            format!("{}{}", SEALED_PREFIX, STANDARD.encode(combined))
        }
        Err(_) => plain.to_string(),
    }
}

pub fn is_sealed(stored: &str) -> bool {
    stored.starts_with(SEALED_PREFIX)
}

pub fn open_secret(stored: &str) -> CoreResult<String> {
    let encoded = match stored.strip_prefix(SEALED_PREFIX) {
        Some(e) => e,
        None => return Ok(stored.to_string()),
    };
    let key = encryption_key(false).ok_or_else(|| {
        CoreError::Storage(
            "session key is missing from the OS keyring; please log in again".to_string(),
        )
    })?;
    let combined = STANDARD.decode(encoded).map_err(|_| {
        CoreError::Storage("stored session is corrupted; please log in again".to_string())
    })?;
    if combined.len() < 13 {
        return Err(CoreError::Storage(
            "stored session is corrupted; please log in again".to_string(),
        ));
    }
    let (nonce, ciphertext) = combined.split_at(12);
    let cipher = ChaCha20Poly1305::new(&key);
    let plain = cipher
        .decrypt(Nonce::from_slice(nonce), ciphertext)
        .map_err(|_| {
            CoreError::Storage(
                "stored session could not be decrypted; please log in again".to_string(),
            )
        })?;
    String::from_utf8(plain)
        .map_err(|_| CoreError::Storage("stored session is corrupted".to_string()))
}
const KEYRING_USERNAME: &str = "microsoft-auth-tokens";
const KEYRING_PROFILE_USERNAME: &str = "microsoft-profile";

pub struct TokenStore;

impl TokenStore {
    pub fn save(tokens: &AuthTokens) -> CoreResult<()> {
        let entry = Entry::new(KEYRING_SERVICE, KEYRING_USERNAME)
            .map_err(|e| CoreError::Storage(format!("keyring access failed: {e}")))?;

        let json = serde_json::to_string(tokens)?;
        entry
            .set_password(&json)
            .map_err(|e| CoreError::Storage(format!("failed to store tokens: {e}")))?;

        Ok(())
    }

    pub fn load() -> CoreResult<Option<AuthTokens>> {
        let entry = Entry::new(KEYRING_SERVICE, KEYRING_USERNAME)
            .map_err(|e| CoreError::Storage(format!("keyring access failed: {e}")))?;

        match entry.get_password() {
            Ok(json) => {
                let tokens: AuthTokens = serde_json::from_str(&json)?;
                Ok(Some(tokens))
            }
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(e) => Err(CoreError::Storage(format!("failed to read tokens: {e}"))),
        }
    }

    pub fn delete() -> CoreResult<()> {
        let entry = Entry::new(KEYRING_SERVICE, KEYRING_USERNAME)
            .map_err(|e| CoreError::Storage(format!("keyring access failed: {e}")))?;

        match entry.delete_credential() {
            Ok(()) => Ok(()),
            Err(keyring::Error::NoEntry) => Ok(()),
            Err(e) => Err(CoreError::Storage(format!("failed to delete tokens: {e}"))),
        }
    }

    pub fn save_profile(profile: &MinecraftProfile) -> CoreResult<()> {
        let entry = Entry::new(KEYRING_SERVICE, KEYRING_PROFILE_USERNAME)
            .map_err(|e| CoreError::Storage(format!("keyring access failed: {e}")))?;

        let json = serde_json::to_string(profile)?;
        entry
            .set_password(&json)
            .map_err(|e| CoreError::Storage(format!("failed to store profile: {e}")))?;

        Ok(())
    }

    pub fn load_profile() -> CoreResult<Option<MinecraftProfile>> {
        let entry = Entry::new(KEYRING_SERVICE, KEYRING_PROFILE_USERNAME)
            .map_err(|e| CoreError::Storage(format!("keyring access failed: {e}")))?;

        match entry.get_password() {
            Ok(json) => {
                let profile: MinecraftProfile = serde_json::from_str(&json)?;
                Ok(Some(profile))
            }
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(e) => Err(CoreError::Storage(format!("failed to read profile: {e}"))),
        }
    }

    pub fn delete_profile() -> CoreResult<()> {
        let entry = Entry::new(KEYRING_SERVICE, KEYRING_PROFILE_USERNAME)
            .map_err(|e| CoreError::Storage(format!("keyring access failed: {e}")))?;

        match entry.delete_credential() {
            Ok(()) => Ok(()),
            Err(keyring::Error::NoEntry) => Ok(()),
            Err(e) => Err(CoreError::Storage(format!("failed to delete profile: {e}"))),
        }
    }
}
