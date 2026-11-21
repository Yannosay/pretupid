# Pretupid Client

A Fabric client mod for Minecraft 1.21.2 up to 26.3 (1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3), built from one codebase. Motion Blur needs Minecraft 1.21.6 or newer.

## Install

- **Pretupid Launcher:** choose *Install Pretupid Client* on a Fabric or Quilt instance for one of the versions above. The launcher picks the matching build and keeps it up to date.
- **Any other launcher:** put the jar for your Minecraft version from the [releases](https://github.com/Yannosay/pretupid/releases) together with [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder. The client tells you in game when an update is available and installs it in one click.

## Features

- **Mod menu** (Right Shift or `/pretupid`): modules with search, favorites and per-module settings, profiles that switch per server, and a mod manager.
- **Quick menu** (hold G): toggle your starred modules, jump to the HUD editor, emotes or the benchmark. Release over a tile to toggle it, or tap G to keep the menu open.
- **HUD editor:** open it with H (rebindable in *Settings → HUD Editor*), then drag, resize, snap, nudge with the arrow keys, and remove widgets with the trash button or the Delete key. Its toolbar sits at the bottom by default and can move to the top.
- **Modules:** HUD widgets, performance tools, zoom, Freelook (hold Left Alt), a custom crosshair designer with live preview, a block outline editor, Freecam (F6), toggle sprint, Compact Chat, Nick Hider and more. Zoom is on by default; everything else starts off. Every module has a settings page with a key to switch it on and off. HUD labels and waypoint names use the module text color from *Settings → Interface*, which follows your interface color until you change it, and each module can still pick its own.
- **Waypoints:** press N (or type `/pretupid waypoint`) to add, rename, recolour, hide, copy and delete waypoints per world and dimension. Visible waypoints show as pins with a light beam in the world, show their name, distance and height difference when you look at them, point the way from the screen edge, death points clear themselves when you reach them, and Overworld and Nether coordinates convert for you. `/pretupid waypoint add|remove|list` still works.
- **Freecam and Cinematic Studio:** F6 flies the camera away from your player (on servers it cannot pass through blocks or go further than 64 blocks). Press K to drop a camera keyframe and J to open the Cinematic Studio: drag keyframes on a timeline, set zoom, tilt and easing (smooth, linear, ease in, ease out or hold) per keyframe, change the playback speed, keep the camera aimed at you, start from templates (orbit, fly-by, crane, dolly zoom, push in) and play the shot with cinema bars. Render turns a shot into PNG frames at 20 to 60 fps and up to three times your window size in `screenshots/pretupid-cinematic`.
- **Screenies:** F4 captures the world without the HUD at your window size or two or three times larger, then opens Screenie Studio with filters and adjustable text you can drag. F7 opens the Meme Editor with top and bottom, caption and poster layouts and camera angles (face close-up, worm's eye, bird's eye, Dutch angle, fisheye, side profile, over the shoulder) that retake the picture without leaving the editor. Both export at 720p, 1K (default), 2K, 4K or the original size and crop to 16:9, 4:3, 1:1, 4:5 or 9:16, with meme layouts fitted to the crop. Save the PNG to `screenshots/pretupid` or send a copy to a Pretupid friend.
- **Chat emoji:** type `:name:` (for example `:sob:`) or use the emoji button and suggestions in chat. Other Pretupid players see the emoji; the server and players without Pretupid Client get your message without the codes.
- **Performance:** Entity Culling skips entities and block entities that are far away or fully hidden behind solid blocks (checked on the render thread within a fixed time budget; players and named entities are always drawn). Particle Limiter, FPS Boost and Dynamic FPS add lighter settings and a lower frame rate in the background.
- **Benchmark:** `/pretupid benchmark` measures average FPS and 1% lows for 10 seconds with the performance modules off, then 10 seconds with them on, with the FPS limit and VSync off during the test. Your settings are restored afterwards.
- **Cosmetics:** the Pretupid badge, a nametag you style yourself (color animation, name motion such as wave, bounce, float, hop or shake, two colors, speed and bold, with presets to start from), capes (upload your own, pick an official one, or equip a Minecraft cape from your account) with optional cape physics, hats, animated wings, a knight shield and a jetpack for your back, floating pets, trails and emotes. Other Pretupid players see them; new official capes, hats, back items, pets, trails and emotes arrive without a client update.
- **Emotes:** the emote wheel holds six emotes. The first six are picked for you, and you can swap, replace or empty slots in *Cosmetics → Emotes*. Hold the emote wheel key, point at an emote and release (or press 1–6), or give any emote its own hotkey in the same tab. Emotes keep playing while you walk and stop when you sneak; both can be changed in the same tab.
- **Skins:** a skin library with Classic/Slim detection, your current account skin, and one-click apply that also updates your own model right away.
- **Friends:** friend requests, chat, see which server friends play on, and invite friends to your server or open your world to friends only.
- **News:** announcements from the Pretupid team on the main menu.
- **Main menu:** the vanilla menu with smoother animations, plus panels for friends online, the latest news and a one-click rejoin of your last server. Both can be turned off in the settings.
- **Server compatibility:** modules a server does not allow are paused while you play there and resume when you leave.
- **Settings that follow you:** settings are mirrored to `~/.pretupid/client` and restored in new instances. Set `PRETUPID_CLIENT_SETTINGS_DIR` to use another folder.

## For server owners

Servers can pause Pretupid Client modules by sending a `pretupid:rules` play payload containing a UTF-8 JSON string such as `{"disable": ["zoom", "fullbright"]}`. Type `/pretupid serverrules` in game to list every module ID; click an ID or the example to copy it. Modules that give an advantage in PvP are highlighted, and the example already disables all of them. The command only shows information that ships with the client and needs no Pretupid account. Rules can also be requested from the Pretupid team so they apply without any server plugin.

## Online services and privacy

Online features use Pretupid services. Your account is verified with the chat signing key Mojang issues to your account and your Mojang-signed profile; your access token never leaves your computer. Pretupid stores your player ID and name, your cosmetic choices, friends and friend messages. Messages are kept for 30 days. Everything can be switched off in *Settings → Online & social*, and the client then works fully offline.

## Building

Requires JDK 25.

```bash
./gradlew buildAllVersions
```

This builds a jar for every Minecraft version in `targets/` and writes them, together with `client-manifest.json`, to `build/all-versions/`. Shared code lives in `src/main`; each version's differences live in a small bridge under `bridges/` (named in that version's `target.properties`), and `python scripts/check-mixins.py` checks every mixin against every version's game jar. `./gradlew runClient` starts a development client for the default version; add `-Ptarget=1.21.11` (or any folder in `targets/`) to work on another version, for example `./gradlew -Ptarget=26.2 runClientGameTest`.

## Credits

Chat emoji use [Twemoji](https://github.com/jdecked/twemoji) by Twitter, Inc. and other contributors, licensed under [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/), with names from [emoji-data](https://github.com/iamcal/emoji-data) (MIT). `scripts/generate-emoji.py` in the repository root rebuilds the emoji font from the `emoji-datasource-twitter` package.
