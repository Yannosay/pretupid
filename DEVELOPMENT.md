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

The website in `frontend-web/` also serves the client API (`server/api`). It runs as the Cloudflare Worker `pretupid-web` with a D1 database (`DB`) and an R2 bucket (`CONTENT`), configured in `frontend-web/wrangler.toml`. Database migrations live in `frontend-web/migrations`; apply new ones with `npx wrangler d1 migrations apply pretupid --remote` before deploying.

Real-time events (emotes, cosmetic changes, friend notifications) go through the `pretupid-realtime` Worker in `frontend-web/realtime`. Every client keeps one WebSocket open. Each player is a `Peer` Durable Object, and players on the same server share a `Room` Durable Object that relays events to everyone in it. The API pushes friend events to a player's `Peer`. Deploy it with `npx wrangler deploy -c realtime/wrangler.toml`; it needs the `PRESENCE_SECRET` secret with the same value as `NUXT_PRESENCE_SECRET`.

Local run:

```bash
cd frontend-web
npm run build
npx wrangler d1 migrations apply pretupid --local
npx wrangler dev
```

Local secrets go into `frontend-web/.dev.vars` (ignored by git): `NUXT_PRESENCE_SECRET`, `NUXT_ADMIN_SESSION_SECRET` and `NUXT_ADMIN_KEY`, each at least 32 random characters. In production they are Worker secrets (`npx wrangler secret bulk <file>`). The admin console is at `/admin`.

## Releases

The launcher and the client are released separately, each as its own GitHub release on a `v<version>` tag (no `client-` or `launcher-` prefix):

- **Pretupid Launcher v<version>**: the tag matches the launcher version (`crates/tauri-app/tauri.conf.json`, the workspace `Cargo.toml` and the `package.json` files). The release workflow builds Windows, macOS (Apple Silicon and Intel) and Linux bundles, signs them and publishes them with the `latest.json` update manifest. Notes go in `.github/release-notes/launcher-v<version>.md`. It needs the `TAURI_SIGNING_PRIVATE_KEY` and `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` repository secrets; the matching public key is in `tauri.conf.json`.
- **Pretupid Client v<version>**: the tag matches `mod_version` in `pretupid-client/gradle.properties`. The workflow builds the jar and attaches it with a `client-manifest.json` holding its hashes (the launcher and the in-game updater only install builds that match those hashes). Notes go in `.github/release-notes/client-v<version>.md`.

The workflow decides from the tag which of the two it builds; a tag that matches neither version fails. It then names the release, adds the notes and marks it as the latest release, and GitHub attaches the source archives. Bump only the versions that changed. When both change, push the client tag first and the launcher tag last, so the launcher release stays the one marked latest (launchers fall back to its `latest.json`).

Update notes reach players from these releases: the in-game updater and the launcher show the body of the client release, the launcher's What's new screen shows the body of the launcher release, and the website picks each download from the newest release that carries it. Older combined `Pretupid v<version>` releases keep working.

## Assets

- `assets/branding/pretupid_logo_512x.png` is the icon and `pretupid_logo_full.png` the wordmark. Both are hand-made source files: never edit them. `npm run sync-branding` regenerates the app icons from the icon, and the client build derives its icon, menu logo and nametag badge from it.
- Hats, back items and pets are catalog cosmetics like capes, trails and emotes: admins create and edit their boxes, colors, optional textures and animations in the admin page, and players get changes without a client update. The client bundles the starting set in `pretupid-client/src/main/resources/assets/pretupid/wearables/defaults.json` for its first offline start; keep it in sync with `frontend-web/migrations/0005_wearables.sql` when the starting set changes.
- Nametag styles are not catalog cosmetics: players combine color, motion, colors, speed and bold freely in the client, and the presence service shares the exact combination. The presets players can start from live only in `pretupid-client/src/main/resources/assets/pretupid/nametags/defaults.json`.
- `npm run dev:desktop` serves the launcher UI in a browser with a mocked backend (`frontend/src/dev/tauriMock.ts`), which is handy for layout work.

## Conventions

- Shared styles live in `packages/design-system`. Global stylesheets `@use 'design-system'`; component styles `@use 'design-system/mixins' as *`.
- Launcher windows talk through Tauri events (`main://navigate`, `tray-panel://launch`, `settings://changed`, `instances://changed`, `modpack://*`), never DOM events.
- `Cargo.lock` and the `package-lock.json` files are committed. Build output is not.
