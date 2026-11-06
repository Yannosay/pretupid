use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct CrashHint {
    pub title: String,
    pub detail: String,
}

struct Pattern {
    needles: &'static [&'static str],
    title: &'static str,
    detail: &'static str,
}

const PATTERNS: &[Pattern] = &[
    Pattern {
        needles: &["java.lang.OutOfMemoryError", "Could not reserve enough space for object heap"],
        title: "Out of memory",
        detail: "Minecraft ran out of RAM. Raise the maximum memory in the instance settings, or lower it if your PC has little free memory.",
    },
    Pattern {
        needles: &["UnsupportedClassVersionError", "has been compiled by a more recent version of the Java Runtime"],
        title: "Wrong Java version",
        detail: "A mod or the loader needs a newer Java. Clear the custom Java path so the launcher picks the right Java automatically.",
    },
    Pattern {
        needles: &["Incompatible mods found", "Incompatible mod set", "requires version", "which is missing", "Missing or unsupported mandatory dependencies", "Mod resolution failed"],
        title: "Missing or incompatible mod",
        detail: "A mod needs another mod (or a different version of it). Check the log above for the mod name and install the missing dependency from Browse.",
    },
    Pattern {
        needles: &["DuplicateModsFoundException", "Found duplicate mods", "Duplicate mods found", "duplicate mod"],
        title: "Duplicate mods",
        detail: "The same mod is installed twice. Open the Content tab and remove one of the copies.",
    },
    Pattern {
        needles: &["Mixin apply failed", "MixinApplyError", "InvalidInjectionException", "Mixin transformation of"],
        title: "Mod conflict",
        detail: "Two mods are fighting over the same code. Disable recently added mods one by one to find the culprit.",
    },
    Pattern {
        needles: &["was compiled for a different Minecraft version", "is not compatible with Minecraft", "requires minecraft"],
        title: "Mod made for another Minecraft version",
        detail: "At least one mod targets a different game version. Run Check for updates or replace it with a matching version.",
    },
    Pattern {
        needles: &["EXCEPTION_ACCESS_VIOLATION", "Pixel format not accelerated", "GLFW error 65542", "WGL: The driver does not appear to support OpenGL", "No OpenGL context found"],
        title: "Graphics driver problem",
        detail: "Your graphics driver crashed or does not support the required OpenGL version. Update your GPU driver and turn off shader packs.",
    },
    Pattern {
        needles: &["Failed to load native library", "UnsatisfiedLinkError"],
        title: "Native library failed to load",
        detail: "A native file could not be loaded. Use Repair on this instance so the launcher re-downloads the game files.",
    },
    Pattern {
        needles: &["ZipException", "zip END header not found", "Invalid or corrupt jarfile", "error in opening zip file"],
        title: "Corrupted file",
        detail: "A jar file is damaged. Use Repair on this instance to re-download mods and game files.",
    },
    Pattern {
        needles: &["Unrecognized option", "Could not create the Java Virtual Machine", "Invalid maximum heap size"],
        title: "Invalid Java arguments",
        detail: "Java rejected one of the launch arguments. Remove custom JVM arguments in the instance settings.",
    },
];

pub fn analyze(lines: &[String]) -> Vec<CrashHint> {
    let mut hints = Vec::new();
    for pattern in PATTERNS {
        let hit = lines
            .iter()
            .any(|line| pattern.needles.iter().any(|needle| line.contains(needle)));
        if hit {
            hints.push(CrashHint {
                title: pattern.title.to_string(),
                detail: pattern.detail.to_string(),
            });
        }
    }
    hints
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_memory_and_java() {
        let lines = vec![
            "Exception in thread main java.lang.OutOfMemoryError: Java heap space".to_string(),
            "java.lang.UnsupportedClassVersionError: x".to_string(),
        ];
        let hints = analyze(&lines);
        assert_eq!(hints.len(), 2);
        assert!(analyze(&["all good".to_string()]).is_empty());
    }
}
