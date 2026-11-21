<picture>
	<img alt="Modrinth Monorepo Cover" src=".github/assets/monorepo_cover.png">
</picture>

![Lines of Code](https://img.shields.io/endpoint?url=https://loctopus.creeperkatze.dev/github/Yannosay/pretupid/badge?style=flat&logoColor=white&color=5d5bf5&style=for-the-badge)
![Issues](https://img.shields.io/github/issues-raw/Yannosay/pretupid?color=5d5bf5&label=issues&style=for-the-badge)
![Pull Requests](https://img.shields.io/github/issues-pr-raw/Yannosay/pretupid?color=5d5bf5&label=PRs&style=for-the-badge)
![Contributors](https://img.shields.io/github/contributors/Yannosay/pretupid?color=5d5bf5&label=contributors&style=for-the-badge)
![Commit Activity](https://img.shields.io/github/commit-activity/m/Yannosay/pretupid?color=5d5bf5&label=commits&style=for-the-badge)
![Last Commit](https://img.shields.io/github/last-commit/Yannosay/pretupid?color=5d5bf5&label=last%20commit&style=for-the-badge)

# Pretupid Monorepo

Welcome to the Pretupid Monorepo, which is the main codebase for the **Pretupid website**, **app**, and **client** and created and primarily managed by [Yannosay Productions](https://yannosay.com).

#### This repo holds three different projects, which are the following:
- **Pretupid Launcher** — the desktop app for Windows, macOS and Linux
- **Pretupid Client** — a Fabric mod with a mod menu, a draggable HUD, performance tools, cosmetics, friends and news. See [`pretupid-client/`](pretupid-client/README.md).
- **Pretupid Website** — the frontend website which feature the projects above


#### If you're not a developer and you've stumbled upon this repository, you have multiple ways to access the projects directly:

- Via web interface
  - Download the latest release of the app [here](https://pretupid.yannosay.com/download).
- Via GitHub
  - Download the latest release or the monorepo source [here](https://github.com/Yannosay/pretupid/releases).

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

## License

All content in this monorepo are licensed under their respective licenses.

> [!NOTE]
> Pretupid is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft. Fonts in `assets/fonts` are licensed under the SIL Open Font License; their license files are next to them.
