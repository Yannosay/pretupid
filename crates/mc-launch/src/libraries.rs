use std::collections::{HashMap, HashSet};

use crate::models::{Arguments, Artifact, Library, Rule, VersionDetails};

pub const MOJANG_LIBRARIES: &str = "https://libraries.minecraft.net/";

pub struct MavenCoord {
    pub group: String,
    pub artifact: String,
    pub version: String,
    pub classifier: Option<String>,
    pub extension: String,
}

impl MavenCoord {
    pub fn parse(coord: &str) -> Option<Self> {
        let coord = coord.trim().trim_start_matches('[').trim_end_matches(']');
        let (main, extension) = match coord.split_once('@') {
            Some((m, e)) => (m, e.to_string()),
            None => (coord, "jar".to_string()),
        };
        let parts: Vec<&str> = main.split(':').collect();
        if parts.len() < 3 || parts.iter().any(|p| p.is_empty()) {
            return None;
        }
        Some(Self {
            group: parts[0].to_string(),
            artifact: parts[1].to_string(),
            version: parts[2].to_string(),
            classifier: parts.get(3).map(|c| c.to_string()),
            extension,
        })
    }

    pub fn path(&self) -> String {
        let file = match &self.classifier {
            Some(c) => format!(
                "{}-{}-{}.{}",
                self.artifact, self.version, c, self.extension
            ),
            None => format!("{}-{}.{}", self.artifact, self.version, self.extension),
        };
        format!(
            "{}/{}/{}/{}",
            self.group.replace('.', "/"),
            self.artifact,
            self.version,
            file
        )
    }

    pub fn identity(&self) -> String {
        match &self.classifier {
            Some(c) => format!("{}:{}:{}", self.group, self.artifact, c),
            None => format!("{}:{}", self.group, self.artifact),
        }
    }
}

pub fn maven_path(coord: &str) -> Option<String> {
    MavenCoord::parse(coord).map(|c| c.path())
}

pub fn is_safe_relative_path(path: &str) -> bool {
    if path.is_empty() || path.starts_with('/') || path.starts_with('\\') || path.contains(':') {
        return false;
    }
    path.split(['/', '\\'])
        .all(|segment| !segment.is_empty() && segment != "." && segment != "..")
}

pub fn current_os() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

pub fn current_arch() -> &'static str {
    if cfg!(target_arch = "x86_64") {
        "x86_64"
    } else if cfg!(target_arch = "aarch64") {
        "arm64"
    } else if cfg!(target_arch = "x86") {
        "x86"
    } else {
        "unknown"
    }
}

fn os_matches(name: Option<&str>, arch: Option<&str>) -> bool {
    let name_ok = match name {
        None | Some("") => true,
        Some(n) => n == current_os(),
    };
    let arch_ok = match arch {
        None | Some("") => true,
        Some(a) => a == current_arch() || (a == "x86" && current_arch() == "x86"),
    };
    name_ok && arch_ok
}

pub fn rules_allow(rules: &[Rule], features: &HashMap<&str, bool>) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        let os_ok = match &rule.os {
            Some(os) => os_matches(os.name.as_deref(), os.arch.as_deref()),
            None => true,
        };
        let features_ok = match &rule.features {
            Some(required) => required
                .iter()
                .all(|(key, value)| features.get(key.as_str()).copied().unwrap_or(false) == *value),
            None => true,
        };
        if os_ok && features_ok {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

pub fn json_rules_allow(rules: Option<&serde_json::Value>, features: &HashMap<&str, bool>) -> bool {
    match rules {
        None | Some(serde_json::Value::Null) => true,
        Some(value) => match serde_json::from_value::<Vec<Rule>>(value.clone()) {
            Ok(parsed) => rules_allow(&parsed, features),
            Err(_) => false,
        },
    }
}

pub fn library_allowed(library: &Library) -> bool {
    match &library.rules {
        Some(rules) => rules_allow(rules, &HashMap::new()),
        None => true,
    }
}

pub fn normalize_library(library: &mut Library) {
    if library.downloads.artifact.is_some() || library.natives.is_some() {
        if let Some(artifact) = library.downloads.artifact.as_mut() {
            if artifact.path.is_empty() {
                if let Some(path) = maven_path(&library.name) {
                    artifact.path = path;
                }
            }
        }
        return;
    }
    let path = match maven_path(&library.name) {
        Some(p) => p,
        None => return,
    };
    let base = library
        .url
        .clone()
        .filter(|u| !u.trim().is_empty())
        .unwrap_or_else(|| MOJANG_LIBRARIES.to_string());
    let base = if base.ends_with('/') {
        base
    } else {
        format!("{}/", base)
    };
    library.downloads.artifact = Some(Artifact {
        url: format!("{}{}", base, path),
        path,
        sha1: library.sha1.clone().unwrap_or_default(),
        size: library.size.unwrap_or(0),
    });
}

pub fn library_identity(library: &Library) -> String {
    MavenCoord::parse(&library.name)
        .map(|c| c.identity())
        .unwrap_or_else(|| library.name.clone())
}

pub fn merge_loader_profile(target: &mut VersionDetails, profile: VersionDetails) {
    if !profile.main_class.is_empty() {
        target.main_class = profile.main_class;
    }

    if let Some(profile_args) = profile.arguments {
        let target_args = target.arguments.get_or_insert_with(Arguments::default);
        target_args.jvm.extend(profile_args.jvm);
        target_args.game.extend(profile_args.game);
    }

    if profile.minecraft_arguments.is_some() {
        target.minecraft_arguments = profile.minecraft_arguments;
    }

    let mut incoming: Vec<Library> = profile.libraries;
    for lib in incoming.iter_mut() {
        normalize_library(lib);
    }
    let incoming_ids: HashSet<String> = incoming
        .iter()
        .filter(|l| l.natives.is_none())
        .map(library_identity)
        .collect();

    target
        .libraries
        .retain(|l| l.natives.is_some() || !incoming_ids.contains(&library_identity(l)));

    let mut seen: HashSet<String> = HashSet::new();
    let mut deduped: Vec<Library> = Vec::new();
    for lib in incoming.into_iter().rev() {
        let key = if lib.natives.is_some() {
            lib.name.clone()
        } else {
            library_identity(&lib)
        };
        if seen.insert(key) {
            deduped.push(lib);
        }
    }
    deduped.reverse();

    let mut combined = deduped;
    combined.append(&mut target.libraries);
    target.libraries = combined;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven_paths() {
        assert_eq!(
            maven_path("net.fabricmc:fabric-loader:0.15.0").unwrap(),
            "net/fabricmc/fabric-loader/0.15.0/fabric-loader-0.15.0.jar"
        );
        assert_eq!(
            maven_path("[net.minecraft:client:1.20.1-20230612.114412:srg@jar]").unwrap(),
            "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar"
        );
        assert_eq!(
            maven_path("de.oceanlabs.mcp:mcp_config:1.20.1@zip").unwrap(),
            "de/oceanlabs/mcp/mcp_config/1.20.1/mcp_config-1.20.1.zip"
        );
        assert!(maven_path("broken").is_none());
    }

    #[test]
    fn relative_paths() {
        assert!(is_safe_relative_path("mods/sodium.jar"));
        assert!(is_safe_relative_path("config/a/b.toml"));
        assert!(!is_safe_relative_path("../evil.jar"));
        assert!(!is_safe_relative_path("mods/../../evil"));
        assert!(!is_safe_relative_path("C:/Windows/evil"));
        assert!(!is_safe_relative_path("/etc/passwd"));
        assert!(!is_safe_relative_path(""));
    }

    #[test]
    fn feature_rules() {
        let rules: Vec<Rule> =
            serde_json::from_str(r#"[{"action":"allow","features":{"is_demo_user":true}}]"#)
                .unwrap();
        let mut features = HashMap::new();
        assert!(!rules_allow(&rules, &features));
        features.insert("is_demo_user", true);
        assert!(rules_allow(&rules, &features));
    }

    #[test]
    fn merge_replaces_conflicting_versions() {
        let mut base: VersionDetails = serde_json::from_str(
            r#"{"id":"1.20.1","mainClass":"a","libraries":[{"name":"org.ow2.asm:asm:9.1","downloads":{"artifact":{"path":"x","sha1":"","size":0,"url":"u"}}}]}"#,
        )
        .unwrap();
        let profile: VersionDetails = serde_json::from_str(
            r#"{"id":"fabric","mainClass":"b","libraries":[{"name":"org.ow2.asm:asm:9.6","url":"https://maven.fabricmc.net/"}]}"#,
        )
        .unwrap();
        merge_loader_profile(&mut base, profile);
        assert_eq!(base.main_class, "b");
        assert_eq!(base.libraries.len(), 1);
        assert_eq!(base.libraries[0].name, "org.ow2.asm:asm:9.6");
        assert_eq!(
            base.libraries[0].downloads.artifact.as_ref().unwrap().url,
            "https://maven.fabricmc.net/org/ow2/asm/asm/9.6/asm-9.6.jar"
        );
    }
}
