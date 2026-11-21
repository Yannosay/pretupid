Pretupid Client 0.1.8 adds a Minimap, a Speedrun Timer and a Now Playing music player, and makes waypoints and the main menu cleaner.

## What's new

### Minimap
- A square or round terrain map that can rotate with you, with adjustable size and zoom.
- Shows your waypoints and keeps them at the edge when they are out of range.
- Keeps recent chunks in memory and writes nothing to disk.

### Speedrun Timer
- Starts on your first input in a new world and pauses with the game.
- Splits when you enter the Nether and the End, stops at the credits and compares against your personal best.
- Runs are saved per world. Use the start and reset keys or `/pretupid speedrun start|pause|reset`.

### Now Playing
- Shows the current Minecraft song when a new one starts.
- Press M to skip, loop one song, queue songs, play any track or go back to dynamic music.
- Stays hidden while music is muted, unless you turn that on.

### Waypoints
- Waypoints now only show in their own dimension. The Overworld and Nether link is an option and starts off.
- Diagonal direction arrows now match the straight ones.
- Deleting asks for a second click, and the Waypoints module has a Manage button.

### Interface
- **Main menu:** the large glow at the bottom is gone. Light particles now have depth and follow your mouse, and a thin marker glides between buttons.
- **Modules:** long descriptions only scroll while you hover over them.
- **Emote wheel:** removed the "Point and release" hint.

## Downloads
- **Client mod:** one jar per Minecraft version, for example `pretupid-client-0.1.8+mc26.3.jar` or `pretupid-client-0.1.8+mc1.21.4.jar`
- **Source:** the source code archives below

The launcher updates Pretupid Client to 0.1.8 on its own, and the client offers the update in-game as well.
