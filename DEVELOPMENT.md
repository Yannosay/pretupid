# Development

## Setup

```bash
npm install
npm run install:all
```

| Command | What it does |
| --- | --- |
| `npm run tauri` | Launcher in development mode |
| `npm run dev:desktop` | Launcher interface only, in the browser |
| `npm run dev:web` | Website |
| `npm run check` | Builds and type-checks everything |
| `npm run tauri:build` | Release build and installer |
| `npm run format:rust` / `npm run lint:rust` | Rust formatting and lints |

Pretupid Client: `cd pretupid-client && ./gradlew build` (JDK 25). `./gradlew runClient` starts a development client; set `PRETUPID_CLIENT_API=http://127.0.0.1:8788/api/client/v1` to use a local service.

## Online services

The website in `frontend-web/` also serves the client API (`server/api`). It runs on Cloudflare Pages with a D1 database (`DB`) and an R2 bucket (`CONTENT`), configured in `frontend-web/wrangler.toml`. Database migrations live in `frontend-web/migrations`.

Local run:

```bash
cd frontend-web
npm run build
npx wrangler d1 migrations apply pretupid --local
npx wrangler pages dev .output
```

Local secrets go into `frontend-web/.dev.vars` (ignored by git): `NUXT_PRESENCE_SECRET`, `NUXT_ADMIN_SESSION_SECRET` and `NUXT_ADMIN_KEY`, each at least 32 random characters. In production they are Pages secrets. The admin console is at `/admin`.

## Releases

- **Launcher:** bump the version in `crates/tauri-app/tauri.conf.json` and the workspace `Cargo.toml`, then push a `v*` tag. The release workflow builds, signs and publishes the installer with its `latest.json` update manifest. It needs the `TAURI_SIGNING_PRIVATE_KEY` and `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` repository secrets; the matching public key is in `tauri.conf.json`.
- **Client:** bump `mod_version` in `pretupid-client/gradle.properties`, then push a `client-v*` tag. The workflow publishes the jar and a `client-manifest.json` with its hashes; the launcher and the in-game updater only install builds that match those hashes.

## Conventions

- Shared styles live in `packages/design-system`. Global stylesheets `@use 'design-system'`; component styles `@use 'design-system/mixins' as *`.
- Launcher windows talk through Tauri events (`main://navigate`, `tray-panel://launch`, `settings://changed`, `instances://changed`, `modpack://*`), never DOM events.
- `Cargo.lock` and the `package-lock.json` files are committed. Build output is not.
