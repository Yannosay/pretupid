# Pretupid

Pretupid is a Minecraft launcher and client by Yannosay Productions.

- **Pretupid Launcher** — a fast desktop launcher for Windows with Fabric, Quilt, Forge and NeoForge instances, Modrinth mods and modpacks, skins and checksum-verified downloads.
- **Pretupid Client** — a Fabric mod with a mod menu, a draggable HUD, performance tools, cosmetics, friends and news. See [`pretupid-client/`](pretupid-client/README.md).

Download the latest release from [pretupid.yannosay.com](https://pretupid.yannosay.com) or the [GitHub releases](https://github.com/Yannosay/pretupid/releases).

## Repository

| Path | Contents |
| --- | --- |
| `crates/` | Rust workspace: launcher core, launch pipeline, Modrinth client and the Tauri app |
| `frontend/` | Launcher interface (Vue 3) |
| `frontend-web/` | Website and Pretupid online services (Nuxt on Cloudflare Pages) |
| `pretupid-client/` | Pretupid Client mod (Fabric, Java 25) |
| `packages/design-system/` | Shared styles |
| `assets/` | Shared branding and fonts |

## Building

Requirements: Node.js 20.19+, the Rust toolchain from `rust-toolchain.toml`, the Tauri 2 prerequisites, and JDK 25 for the client.

```bash
npm install
npm run install:all
npm run tauri
```

```bash
cd pretupid-client
./gradlew build
```

See [DEVELOPMENT.md](DEVELOPMENT.md) for more commands.

## Security

Please report vulnerabilities privately through [GitHub security advisories](https://github.com/Yannosay/pretupid/security/advisories/new) instead of public issues.

## Legal

Pretupid is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft. Fonts in `assets/fonts` are licensed under the SIL Open Font License; their license files are next to them.
