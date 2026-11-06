# Pretupid Launcher

A modern, fast, and customizable Minecraft launcher built with Tauri 2, Rust, and Vue 3 by Yannosay Productions.

Pretupid Launcher is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft.

## Overview

Pretupid Launcher is a desktop Minecraft launcher focused on three things:

- **Speed.** Rust backend, Tauri shell, and a fast boot path that paints instantly from local state and defers network work until after the splash.a
- **Customization.** Theme, accent color, and corner radius are user-controlled and apply to every window to make the launcher feel like your onwn.
- **Integration.** First-class Modrinth browsing, mod and modpack installation, instance management, and Microsoft authentication.

The project is organized as a Rust workspace with a Tauri desktop application and a separate Nuxt marketing site.

## Architecture

```
pretupid-launcher/
├── crates/
│   ├── core/               Domain layer: instances, settings, auth, skins (SQLite)
│   ├── mc-launch/          Minecraft version resolution, downloads, launch pipeline
│   ├── modrinth-client/    Modrinth API client (search, projects, versions, bulk lookups)
│   └── tauri-app/          Tauri shell, commands, tray, state wiring
├── frontend/               Tauri SPA (Vue 3, Vite, Pinia, Vue Router, SCSS)
├── frontend-web/           Marketing site (Nuxt 3, SSR, Cloudflare Pages preset)
└── edit.ps1                Agent interaction script (excluded from source control)
```

### Crates

**`pretupid-core`** — the domain layer. Owns persistence through `sqlx` against a local SQLite database. Provides:

- `InstanceRepository` — instances, per-instance settings, and installed mods.
- `SettingsRepository` — global settings persisted as a single JSON row.
- `SkinRepository` — up to five skin slots per profile with active-slot tracking.
- `OAuthClient` — the full Microsoft → Xbox Live → XSTS → Minecraft auth chain, token refresh, and profile persistence.

**`mc-launch`** — the launch pipeline. Handles:

- Mojang version manifest resolution with on-disk caching.
- Parallel download of the Minecraft client, libraries, assets index, and asset objects.
- Native library extraction for the current OS.
- Fabric, Quilt, Forge, and NeoForge installation, including the Forge and NeoForge installer invocation.
- Java runtime detection across `JAVA_HOME`, `PATH`, and standard install roots, plus automatic download from the Adoptium API when the required major version is missing.
- Argument construction with placeholder substitution and rule evaluation, using an argfile for Java 9 and newer.
- Process spawn, live log streaming, and cancellation via a `watch` channel.

**`modrinth-client`** — Modrinth API v2 client. Provides search, project lookup, version listing, tag metadata, and bulk hash-to-version and project-id-to-metadata lookups used by modpack installs.

**`pretupid` (tauri-app)** — the application shell. Owns the `AppState`, registers every Tauri command, wires the tray icon and the custom tray panel window, and manages cancellation channels per running instance.

### Frontend

The desktop SPA is Vue 3 with Composition API, Pinia for state, Vue Router with hash history, and SCSS using a token-based design system.

Key areas:

- **Boot path.** `index.html` renders an inline boot splash before Vue loads. `main.ts` mounts the app, hides the splash, then defers authentication, instance loading, profile loading, updater checks, and event listeners so the first paint is immediate.
- **State.** Stores: `auth`, `instances`, `launch`, `modrinth`, `notifications`, `profile`, `settings`, `splash`, `updater`.
- **Views.** Home, Library, Instance detail, Profile, Modrinth library. Instance detail shows installed mods with icons, allows removal, and shows launch progress and console output.
- **Modals.** Create instance, create modpack instance, add to instance, skin import, settings.
- **Notifications.** A comprehensive notification store with per-item kind (info/success/warning/error), badge count, mark-all-read, clear-all, dismiss, action buttons, and in-place update for long-running operations like modpack installs.

The tray panel is a second window (`tray-panel`) rendered by the same bundle but mounting `TrayPanel.vue` instead of `App.vue`. It loads the user's settings from local storage so theme, accent, and corner radius apply before paint, and it lists instances for one-click launch.

### Website

The Nuxt 3 site is a separate build targeting Cloudflare Pages. It shares the SCSS token set with the desktop app so both render the same visual language.

## Features

- Microsoft account login with the full Xbox Live and XSTS chain, tokens persisted locally, refresh handled transparently.
- Instance management: create vanilla or modded instances (Fabric, Quilt, Forge, NeoForge), edit settings per instance, delete with full disk cleanup.
- Modrinth integration: browse, filter, and install mods and modpacks. Modpack installs run in the background with live progress and Modrinth-provided mod icons.
- Java auto-install: detects the required major version per Minecraft version and downloads the correct JRE from Adoptium when needed.
- Full resource downloads with progress reporting and cancellation.
- Skin management: up to five slots per profile, import from file or URL, apply to Minecraft, export as PNG, 3D preview with idle, walk, and fly animations.
- Global and per-instance settings: memory bounds, resolution, fullscreen, Java path, extra JVM and game arguments.
- Customizable UI: dark and light themes, accent color presets plus custom, blocky or rounded corner style with adjustable radius.
- System tray panel with quick launch, quick navigation, and Quit.
- Notification center with persistent and transient notifications, action buttons, and per-instance progress tracking.
- Auto-updater via the Tauri updater plugin, with in-splash progress and completion notification.

## Requirements

### Development

- **Node.js** 20.19 or newer.
- **Rust** with the toolchain matching the workspace `rust-version`.
- **Tauri prerequisites** for your platform. See the Tauri 2 prerequisites documentation for your OS.

### Runtime (end users)

- **Windows** 10 or 11, 64-bit.
- WebView2 runtime. Bundled or auto-installed by the Tauri installer.
- Java for the Minecraft version being launched. Auto-installed by the launcher if missing and auto-install is enabled.

## Getting Started

### Install frontend dependencies

```
cd frontend
npm install
```

### Run in development

From the repository root:

```
npm run tauri
```

This starts Vite on port 1420 and launches the Tauri development shell against it. The tray panel window is created at startup alongside the main window.

### Build for production

```
cd frontend
npm run build
cd ..
cargo build --release -p pretupid
```

The Tauri bundler produces an NSIS installer under `crates/tauri-app/target/release/bundle/nsis/`.

## Configuration

### Global settings

Stored in the SQLite database under the application data directory:

- **Windows:** `%APPDATA%PretupidLauncher`
- **Linux and macOS:** `$XDG_DATA_HOME/pretupid-launcher` or `~/.local/share/pretupid-launcher`

Contents:

- Default Java path (optional; empty means auto-detect).
- Auto-install required Java version (boolean).
- Show non-release Minecraft versions (boolean).
- Automatically check for updates (boolean).

### Per-instance settings

Stored in the same database, keyed by instance id:

- Minimum and maximum memory in megabytes.
- Window width and height.
- Fullscreen toggle.
- Optional Java path override.
- Extra JVM arguments.
- Extra game arguments.

### Local UI preferences

Stored in `localStorage` under the key `pretupid-settings`:

- Theme (`dark` or `light`).
- Accent color.
- Edge radius in pixels and last-rounded value.

These are read synchronously at boot so the first frame already reflects the user's chosen theme and colors, including in the tray panel window.

## Data Layout

```
<app data dir>/
├── meta/
│   └── pretupid.db         SQLite database
├── profiles/
│   ├── <instance folder>/  Minecraft game directory per instance
│   │   └── mods/           Installed mod jars
│   └── skins/
│       └── <profile id>/   Skin files per Minecraft profile
├── caches/
│   ├── versions/           Minecraft version JSON and client jars
│   ├── libraries/          Maven libraries
│   ├── assets/             Asset index and asset objects
│   ├── natives/            Extracted native libraries
│   ├── java/               Auto-installed Java runtimes
│   └── tmp/                Scratch space for modpack extraction
└── launcher_logs/
    └── pretupid-launcher.log
```

## Authentication

Pretupid Launcher uses Microsoft OAuth with PKCE. The flow:

1. A code verifier and state are generated and held in memory.
2. The Microsoft authorization URL is opened in a dedicated WebView window.
3. On redirect, the authorization code is exchanged for a Microsoft access token and refresh token.
4. The Microsoft token is exchanged for an Xbox Live token.
5. The Xbox Live token is exchanged for an XSTS token.
6. The XSTS token is exchanged for a Minecraft access token.
7. The Minecraft profile (id and name) is fetched and persisted.

Only the Minecraft access token, the Microsoft refresh token, and the expiry timestamp are persisted. On the next launch, the token is validated against the Minecraft profile endpoint, and refreshed automatically if it has expired.

Logging out removes both the tokens and the persisted profile from the local database.

## Mods and Modpacks

### Mod installation

Adding a mod from the Modrinth view:

1. Fetches the version list for the selected project.
2. Resolves the target version, either the one selected by the user or the latest release.
3. Validates that the version supports the instance's Minecraft version and mod loader.
4. Writes the mod record to the database.
5. Downloads the primary file into the instance's `mods` directory.
6. Rolls back the database record if the download fails.

### Modpack installation

Creating an instance from a Modrinth modpack:

1. Fetches the modpack project and version list.
2. Reads the version's primary file URL.
3. Creates the instance immediately with the modpack's declared Minecraft version and loader.
4. Returns control to the UI so the modal can be closed.
5. Runs the rest of the install in a background task that emits progress events.

The background task:

1. Downloads and extracts the modpack archive to a temporary directory.
2. Reads `modrinth.index.json` and, if the declared Minecraft version or loader differs from what was stored at creation time, updates the instance.
3. Downloads every file declared in the index, skipping files marked `unsupported` on the client side.
4. For every mod jar, looks up its SHA-1 against Modrinth in a single batch request to obtain the version record, then looks up the resolved project ids in a second batch request to obtain titles and icon URLs.
5. Writes a mod record per jar so the mod is visible and removable in the instance detail view.
6. Copies `overrides` and `client-overrides` from the archive into the instance directory.
7. Removes the temporary directory.
8. Emits a completion event, or an error event that also deletes the instance and its directory.

Progress is delivered to the notification center as a single persistent notification that updates in place.

## Tray Integration

The application tray icon supports two interactions:

- **Left click:** focuses the main window.
- **Right click:** opens a custom-styled panel window near the cursor.

The panel is a borderless, always-on-top, taskbar-skipping window. It:

- Loads the user's theme and accent settings from local storage before rendering.
- Lists every instance with a one-click launch action.
- Provides quick navigation to Home, Instances, Profile, and Modrinth, which focuses the main window and routes it.
- Provides Open Launcher and Quit actions.

There is no native OS context menu for the tray icon; the panel is the menu. This is intentional, so the menu inherits the launcher's visual language.

## Notifications

The notification center is opened from the chevron button in the main window's top bar. Notifications have a kind (`info`, `success`, `warning`, `error`), a title, a message, and an optional action button.

Behaviour:

- Auto-dismissing notifications are used for transient events.
- Persistent notifications are used for long-running operations and for errors that require acknowledgment.
- Long-running operations update an existing notification in place rather than appending new ones.
- The chevron shows an unread count badge and marks all as read when opened.

## Updater

The application uses the Tauri updater plugin. Update checks happen:

- Automatically on startup if `auto_update_enabled` is true.
- Manually via the Settings modal.

When an update is available, the splash overlay switches to a determinate progress bar showing download progress. Once installed, a notification informs the user that the update will take effect on the next start.

The updater public key is embedded in `crates/tauri-app/tauri.conf.json`. The corresponding private key must never be committed.

## Project Structure Details

### Frontend scripts

```
npm run dev          Start Vite development server
npm run build        Type-check with vue-tsc and build with Vite
npm run preview      Preview the production build locally
npm run tauri        Launch the Tauri development shell
npm run tauri:build  Build the Tauri application
```

### Rust workspace

```
cargo check                          Type-check the entire workspace
cargo build --release -p pretupid    Build the release binary
cargo run -p pretupid                Run the development binary
```

## Development Notes

### Adding a new Tauri command

1. Implement the command in the relevant module under `crates/tauri-app/src/commands/`.
2. Register it in `crates/tauri-app/src/lib.rs` inside the `tauri::generate_handler!` macro.
3. Add a wrapper in `frontend/src/api/index.ts` using `invoke`.
4. Consume it from a Pinia store or component.

Command arguments are passed to `invoke` as a camelCase-keyed object; Tauri maps them to snake_case Rust parameters automatically.

### Adding a new store

1. Create the store under `frontend/src/stores/`.
2. Use the Composition API style with `defineStore` and `ref` or `computed`.
3. Never mutate reactive state inside a `computed`; use functions and let components call them.
4. If the store needs to communicate with other windows, use the Tauri event system.

### Event channels

The following Tauri events are emitted by the backend and consumed by the frontend:

- `auth-state` — authentication state changed.
- `launch://progress` — launch download progress.
- `launch://log` — a line of launch output.
- `launch://started` — the game process started.
- `launch://failed` — the launch pipeline failed with a message.
- `launch://exited` — the game process exited.
- `modpack://progress` — modpack install progress.
- `modpack://instance-updated` — the modpack declared a different Minecraft version or loader than the instance had.
- `modpack://complete` — modpack install finished.
- `modpack://error` — modpack install failed.
- `tray-panel://launch` — the tray panel requested a launch.
- `instances://changed` — an instance was created or deleted, so the tray panel reloads.

## License

Pretupid Launcher is licensed under the GNU Affero General Public License version 3.0. See `crates/tauri-app/installer-license.txt` for the full text.

The marketing website under `frontend-web/` is licensed under the same terms.

## Disclaimer

NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

Minecraft is a trademark of Mojang Synergies AB. Microsoft and Xbox Live are trademarks of Microsoft Corporation. Modrinth is a service provided by Rinth, Inc.

Pretupid Launcher is an independent project. It downloads and launches Minecraft using the official Mojang version manifest and the official Microsoft authentication service. It does not modify, redistribute, or relicense any Minecraft assets.