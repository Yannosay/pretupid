# Development Guide

This document describes how to work with the Pretupid Launcher monorepo.

## Layout

- crates/ - Rust workspace.
  - core/ - domain layer, SQLite repositories, Microsoft auth.
  - mc-launch/ - version resolution, downloads, launch pipeline, Java runtime management.
  - modrinth-client/ - Modrinth API client.
  - tauri-app/ - Tauri shell, commands, tray, state wiring.
- frontend/ - the Tauri desktop SPA (Vue 3, Vite, Pinia).
- frontend-web/ - the marketing site (Nuxt 3, SSR).
- packages/design-system/ - shared SCSS for both frontends.
- edit.ps1 - agent interaction script; excluded from version control.

## Prerequisites

- Node.js 20.19 or newer.
- Rust stable toolchain (pinned by rust-toolchain.toml).
- Tauri 2 prerequisites for your OS.
- Windows only: WebView2 runtime for running the built launcher.

## First-time setup

    npm install
    npm run install:all

## Common tasks

Build everything and verify it compiles:

    npm run check

Run the desktop app in development mode (Vite + Tauri):

    npm run tauri

Run only the desktop frontend against a mocked backend:

    npm run dev:desktop

Run only the marketing site:

    npm run dev:web

Build the release binary and NSIS installer:

    npm run tauri:build

Format and lint:

    npm run format:rust
    npm run lint:rust

## Release process

1. Bump version in crates/tauri-app/tauri.conf.json and Cargo.toml (workspace).
2. Commit and tag: git tag v0.0.1-alpha
3. Push the tag: git push origin v0.0.1-alpha
4. The GitHub Actions workflow at .github/workflows/release.yml builds the
   NSIS installer, signs it, and publishes it as a GitHub release with a
   latest.json update manifest.
5. The updater endpoint in crates/tauri-app/tauri.conf.json points to the
   latest.json asset of the most recent release.

## Signing key

The Tauri updater requires a signing keypair.

Generate once:

    npm run tauri signer generate -- -w ~/.pretupid-signing/pretupid.key

The public key is committed in crates/tauri-app/tauri.conf.json under
plugins.updater.pubkey. The private key and its password must be stored as
GitHub Actions secrets:

- TAURI_SIGNING_PRIVATE_KEY - contents of the private key file.
- TAURI_SIGNING_PRIVATE_KEY_PASSWORD - the password.

Never commit the private key. *.key is ignored by .gitignore as a safety net.

## Shared design system

Shared SCSS lives in packages/design-system/. Both frontends consume it via
Sass loadPaths, configured to the parent packages/ directory.

Global stylesheets (frontend/src/assets/styles/main.scss and
frontend-web/assets/styles/main.scss) use:

    @use 'design-system';
    @use '_accent';

The _accent.scss file is local to each consumer and defines the three accent
tokens: --accent, --accent-hover, --accent-text. It must load after
design-system so its declarations override the shared defaults.

Vue SFC style scoped blocks use:

    @use 'design-system/mixins' as *;

The direct path is used deliberately so SFCs do not emit the design system's
global :root, reset, or element selectors inside their scoped styles. The
mixins are the only thing an SFC needs from the design system; all tokens are
consumed at runtime via CSS custom properties.

Accent color is intentionally NOT part of the shared token set. The desktop
app defaults to green (#4aff9e) and the web defaults to blue (#4a9eff).

## Cross-window communication

The launcher uses two Tauri windows:

- main - the primary SPA.
- tray-panel - a borderless panel rendered by TrayPanel.vue.

Both share the same JS bundle; main.ts branches on getCurrentWindow().label.

Cross-window communication uses Tauri events, never DOM CustomEvents:

- main://navigate - the tray panel requests a route change in main.
- tray-panel://launch - the tray panel requests launching an instance.
- settings://changed - the settings store broadcasts a settings change to all windows.
- instances://changed - an instance was created or deleted; tray panel reloads.
- modpack://* - modpack install lifecycle events.

DOM events do not cross WebView windows. This was a source of bugs; do not
reintroduce it.

## Repository hygiene

- edit.ps1, context-export/, project-context-export.txt are agent tooling and
  are excluded.
- Do not commit target/, node_modules/, .output/, .nuxt/, dist/.
- Cargo.lock and both package-lock.json files are committed intentionally.

## Troubleshooting

Sass cannot find a shared import:

- Ensure packages/design-system/ exists and contains _index.scss.
- Ensure the frontend's Vite/Nuxt config has the loadPaths entry pointing at
  ../packages.

Tray panel buttons do nothing:

- Check crates/tauri-app/capabilities/default.json lists both main and
  tray-panel in windows.

Windows does not raise the main window on tray left-click:

- The focus_main_window helper uses set_always_on_top(true) before set_focus().
  Some Windows configurations require an additional delay; the code uses 120ms,
  which is sufficient on all tested configurations.

Accent color is wrong in the tray panel:

- Confirm _accent.scss is loaded after design-system in main.scss.
- Confirm the settings store DEFAULT_ACCENT matches the value in _accent.scss.