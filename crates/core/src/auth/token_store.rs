use keyring::Entry;

use crate::error::{CoreError, CoreResult};
use super::models::{AuthTokens, MinecraftProfile};

const KEYRING_SERVICE: &str = "pretupid-launcher";
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