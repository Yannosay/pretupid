# Working on Pretupid

Rules for anyone, human or AI agent, changing this repository. `DEVELOPMENT.md` covers setup, services and releases.

## Hard rules

- No comments in code (NOTE: This rule applies to only AI agents), in any language unless required by given instructions (e.g. if user writes 'provide helpful comments explaining certain code or todo's in source'). Comments a maintainer wrote stay unless they are no longer true.
- Never edit `assets/branding/pretupid_logo_512x.png` or `assets/branding/pretupid_logo_full.png`. Derived copies (app icons, favicons, client textures) are generated from them.
- Do not change the root `README.md` or text a maintainer has rephrased unless asked.
- Every claim on the website, in the launcher or in a README must match a shipped feature. A maintainer will most likely review some texts and rewrite some other. Update the copy in the same change as the feature.
- Keep each file's existing line endings. Many files use CRLF; rewriting them as LF produces whole-file diffs.
- Never commit secrets, `.dev.vars`, `todo.txt`, `run/` folders or build output.

## Project map

| Path | What it is |
| --- | --- |
| `crates/core`, `crates/mc-launch`, `crates/modrinth-client` | Rust launcher logic: instances, downloads, Java, Modrinth |
| `crates/tauri-app` | Tauri commands and windows |
| `frontend/` | Launcher UI (Vue 3, Pinia, Vite) |
| `frontend-web/` | Website and online services (Nuxt on Cloudflare Workers, D1, R2) |
| `packages/design-system`, `packages/ui` | Shared styles and Vue components (`@ui`) |
| `pretupid-client/` | Fabric client mod for Minecraft 26.3 (Java 25, Loom, Mixin) |
| `assets/` | Branding, fonts and other shared assets |

## Before you commit

Run what your change touches. All of it must pass without new warnings.

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cd frontend && npx vue-tsc --noEmit && npx vite build
cd frontend-web && npx nuxi typecheck && npx nuxi build
cd pretupid-client && ./gradlew build
```

## Verifying UI changes

Check visual work with real screenshots, not by reading code.

- Launcher: `npm run dev:desktop` serves the UI with a mocked backend (`frontend/src/dev/tauriMock.ts`). New Tauri commands need a mock case.
- Website: `npm run dev:web`.
- Client: `./gradlew runClient`, then capture the game window. Commands and menus can be checked in a singleplayer world.

## Client conventions

- Minecraft 26.3 names: `GuiGraphicsExtractor`, `extractRenderState`, SDL key codes through `InputConstants`.
- Modules extend `Module`, register in `PretupidClient#registerModules` and need `pretupid.module.<id>.*` keys in `en_us.json`.
- Modules that give an advantage in PvP belong in `ServerCompatibility.COMPETITIVE_MODULES` and in `CLIENT_MODULE_IDS` in `frontend-web/server/utils/serverRules.ts`.
- Work on the render thread must stay within a time budget and fall back to vanilla behaviour when the budget is spent.

## Commits

Small, focused commits with an imperative subject line. Do not push to `main` directly; work on a feature branch.
