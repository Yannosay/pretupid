# Pretupid Client

A Fabric client mod for Minecraft 26.3.

## Install

- **Pretupid Launcher:** choose *Install Pretupid Client* on a Fabric or Quilt instance. The launcher keeps it up to date.
- **Any other launcher:** put the jar from the [releases](https://github.com/Yannosay/pretupid/releases) together with [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder. The client tells you in game when an update is available and installs it in one click.

## Features

- **Mod menu** (Right Shift or `/pretupid`): modules with search, favorites and per-module settings, profiles that switch per server, and a mod manager.
- **Quick menu** (hold G): toggle your starred modules, jump to the HUD editor, emotes or the benchmark. Release over a tile to toggle it, or tap G to keep the menu open.
- **HUD editor:** open it with H (rebindable in *Settings → HUD Editor*), then drag, resize, snap, nudge with the arrow keys, and remove widgets with the trash button or the Delete key. Its toolbar sits at the bottom by default and can move to the top.
- **Modules:** HUD widgets, performance tools, zoom, Freelook (hold Left Alt), a custom crosshair designer with live preview, a block outline editor, waypoints (`/pretupid waypoint add|remove|list`), toggle sprint, Compact Chat, Nick Hider and more. Zoom is on by default; everything else starts off.
- **Performance:** Entity Culling skips entities and block entities that are far away or fully hidden behind solid blocks (checked on the render thread within a fixed time budget; players and named entities are always drawn). Particle Limiter, FPS Boost and Dynamic FPS add lighter settings and a lower frame rate in the background.
- **Benchmark:** `/pretupid benchmark` measures average FPS and 1% lows for 10 seconds with the performance modules off, then 10 seconds with them on, with the FPS limit and VSync off during the test. Your settings are restored afterwards.
- **Cosmetics:** the Pretupid badge, animated name colors, name motion (wave, bounce, float, hop, shake), capes (upload your own, pick an official one, or equip a Minecraft cape from your account) with optional cape physics, hats, animated wings, a knight shield and a jetpack for your back, floating pets, trails and emotes. Other Pretupid players see them; new official capes, trails and emotes arrive without a client update.
- **Emotes:** hold the emote wheel key, point at an emote and release, or give any emote its own hotkey in *Cosmetics → Emotes*. Emotes keep playing while you walk and stop when you sneak; both can be changed in the same tab.
- **Skins:** a skin library with Classic/Slim detection, your current account skin, and one-click apply that also updates your own model right away.
- **Friends:** friend requests, chat, see which server friends play on, and invite friends to your server or open your world to friends only.
- **News:** announcements from the Pretupid team on the main menu.
- **Main menu:** the vanilla menu with smoother animations, plus panels for friends online, the latest news and a one-click rejoin of your last server. Both can be turned off in the settings.
- **Server compatibility:** modules a server does not allow are paused while you play there and resume when you leave.
- **Settings that follow you:** settings are mirrored to `~/.pretupid/client` and restored in new instances. Set `PRETUPID_CLIENT_SETTINGS_DIR` to use another folder.

## For server owners

Servers can pause Pretupid Client modules by sending a `pretupid:rules` play payload containing a UTF-8 JSON string such as `{"disable": ["zoom", "fullbright"]}`. Module IDs are listed in the admin panel under *Server rules*. Rules can also be requested from the Pretupid team so they apply without any server plugin.

## Online services and privacy

Online features use Pretupid services. Your account is verified with the chat signing key Mojang issues to your account and your Mojang-signed profile; your access token never leaves your computer. Pretupid stores your player ID and name, your cosmetic choices, friends and friend messages. Messages are kept for 30 days. Everything can be switched off in *Settings → Online & social*, and the client then works fully offline.

## Building

Requires JDK 25.

```bash
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient` starts a development client.
