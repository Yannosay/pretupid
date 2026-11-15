# Pretupid Client

A Fabric client mod for Minecraft 26.3.

## Install

- **Pretupid Launcher:** choose *Install Pretupid Client* on a Fabric or Quilt instance. The launcher keeps it up to date.
- **Any other launcher:** put the jar from the [releases](https://github.com/Yannosay/pretupid/releases) together with [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder. The client tells you in game when an update is available and installs it in one click.

## Features

- **Mod menu** (Right Shift or `/pretupid`): modules with search, favorites and per-module settings, profiles that switch per server, and a mod manager.
- **HUD editor:** drag, resize, snap, nudge with the arrow keys, and remove widgets with the trash button or the Delete key.
- **Modules:** HUD widgets, FPS and culling tools, zoom, a custom crosshair designer with live preview, toggle sprint, chat tools and more. Zoom is on by default; everything else starts off.
- **Cosmetics:** the Pretupid badge, animated name colors, name motion (wave, bounce, float, hop, shake), capes (upload your own or pick an official one), trails and emotes. Other Pretupid players see them; new official cosmetics arrive without a client update.
- **Friends:** friend requests, chat, see which server friends play on, and invite friends to your server or open your world to friends only.
- **News:** announcements from the Pretupid team on the main menu.
- **Main menu:** smoother animations while keeping the vanilla look. It can be turned off in the settings.

## Online services and privacy

Online features use Pretupid services. Your account is verified the same way a Minecraft server verifies it; your access token never leaves your computer. Pretupid stores your player ID and name, your cosmetic choices, friends and friend messages. Messages are kept for 30 days. Everything can be switched off in *Settings → Online & social*, and the client then works fully offline.

## Building

Requires JDK 25.

```bash
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runClient` starts a development client.
