import type { SiteIconName } from '~/components/SiteIcon.vue'

export interface Feature {
  icon: SiteIconName
  title: string
  description: string
  highlight?: boolean
}

export interface FeatureGroup {
  id: string
  title: string
  subtitle: string
  features: Feature[]
}

export const REPOSITORY_URL = 'https://github.com/Yannosay/pretupid'

export const featureGroups: FeatureGroup[] = [
  {
    id: 'mods',
    title: 'Mods & modpacks',
    subtitle: 'Every major loader, installed and verified for you.',
    features: [
      {
        icon: 'box',
        title: 'The Big 4',
        description:
          'Fabric, Quilt, Forge and NeoForge integrated, plus an rich library from Modrinth.',
        highlight: true
      },
      {
        icon: 'puzzle',
        title: 'Dependencies handled',
        description: 'Installing a mod also installs the mods it needs, picking versions that match your game and loader.',
        highlight: true
      },
      {
        icon: 'refresh',
        title: 'One-click updates',
        description: 'Check every mod, resource pack and shader for updates and upgrade them all at once.',
        highlight: true
      },
      {
        icon: 'toggle',
        title: 'Enable or disable content',
        description: 'Turn mods off without deleting them to track down conflicts in seconds.'
      },
      {
        icon: 'upload',
        title: 'Drag and drop',
        description: 'Drop .jar mods or .zip packs onto an instance. Pretupid recognises them on Modrinth and fills in names and icons.'
      },
      {
        icon: 'layers',
        title: 'Modpack import and export',
        description: 'Install packs straight from Modrinth or from a .mrpack file, and share your own setup as a .mrpack.'
      }
    ]
  },
  {
    id: 'play',
    title: 'Playing',
    subtitle: 'Less clicking around, more time in game.',
    features: [
      {
        icon: 'bolt',
        title: 'Play from anywhere',
        description: 'Start any instance from the home screen, the library, the sidebar or the system tray.',
        highlight: true
      },
      {
        icon: 'alert',
        title: 'Crash helper',
        description:
          'When the game crashes, Pretupid reads the log and tells you the likely cause.',
        highlight: true
      },
      {
        icon: 'server',
        title: 'Join a server on launch',
        description: 'Save a server address per instance and land directly in it when you press Play.'
      },
      {
        icon: 'clock',
        title: 'Playtime tracking',
        description: 'See how long you played each instance and jump back into the last one instantly.'
      },
      {
        icon: 'copy',
        title: 'Duplicate and group',
        description: 'Clone an instance to experiment safely, and organise everything into groups.'
      },
      {
        icon: 'coffee',
        title: 'Java handled',
        description: 'The right Java version is detected or downloaded for every Minecraft release. No manual setup.'
      }
    ]
  },
  {
    id: 'security',
    title: 'Security & privacy',
    subtitle: 'Your account and your PC deserve better than a guess.',
    features: [
      {
        icon: 'check',
        title: 'Verified downloads',
        description: 'Game files, libraries, mods and Java runtimes are checked against their published checksums before use.',
        highlight: true
      },
      {
        icon: 'lock',
        title: 'Encrypted sign-in',
        description: 'Your Microsoft session is encrypted on disk with a key kept in your system keychain (Windows Credential Manager, macOS Keychain or the Linux Secret Service).'
      },
      {
        icon: 'shield',
        title: 'Safe modpacks',
        description: 'Modpack files can only be written inside their own instance folder and only downloaded from trusted hosts.'
      },
      {
        icon: 'eye-off',
        title: 'No telemetry',
        description: 'Pretupid does not track you. There are no analytics and no account other than your Microsoft login.'
      }
    ]
  },
  {
    id: 'comfort',
    title: 'Comfort',
    subtitle: 'Small things that make the launcher feel like yours.',
    features: [
      {
        icon: 'palette',
        title: 'Your colours',
        description: 'Dark or light theme, any accent colour and blocky or rounded corners, synced from this website in one click.'
      },
      {
        icon: 'wrench',
        title: 'Repair in one click',
        description: 'Re-verify and re-download broken game files, loaders and mods without starting over.'
      },
      {
        icon: 'download',
        title: 'Pre-download',
        description: 'Download everything an instance needs ahead of time so the first launch is instant.'
      },
      {
        icon: 'terminal',
        title: 'Readable console',
        description: 'Live game output with search, error highlighting and a one-click copy for bug reports.'
      },
      {
        icon: 'layers',
        title: 'Instance icons',
        description: 'Upload or drop your own picture for an instance, or search every block texture from your installed Minecraft.'
      }
    ]
  },
  {
    id: 'client',
    title: 'Pretupid Client',
    subtitle: 'A free Fabric mod for Minecraft 1.21.2 to 26.3 that you add to any instance in one click.',
    features: [
      {
        icon: 'bolt',
        title: 'Performance',
        description: 'Entity culling skips mobs, chests and signs hidden behind walls. Add a particle limiter, lighter video settings and lower FPS in the background. A built-in benchmark shows the difference on your own PC.'
      },
      {
        icon: 'layers',
        title: 'Movable HUD',
        description: 'FPS, CPS, ping, keystrokes, armor, potions, coordinates, biome, day and time, the block you look at, saturation, item counter, light level and more, placed exactly where you want.'
      },
      {
        icon: 'palette',
        title: 'Cosmetics',
        description: 'Capes, hats, animated wings and back items, floating pets, emotes like the Dead Pose, and a nametag you style yourself: colour animation, motion, two colours, speed and bold. Other Pretupid players see all of it.'
      },
      {
        icon: 'folder',
        title: 'Waypoints',
        description: 'Press N to manage waypoints per world. Coloured markers float in the world with their distance, death points clear themselves when you get back, and Nether and Overworld coordinates convert for you.'
      },
      {
        icon: 'eye-off',
        title: 'Chat and streaming tools',
        description: 'Compact Chat merges spam into one line, Nick Hider swaps your name for an alias, and timestamps and macros are one toggle away.'
      },
      {
        icon: 'key',
        title: 'Quick menu and Freelook',
        description: 'Hold G to toggle your favourite modules or jump to the HUD editor. Hold Left Alt to look around without turning.'
      },
      {
        icon: 'sparkle',
        title: 'Screenies and memes',
        description: 'Press F4 for a screenshot without the HUD at up to three times your screen size. Add filters, text and meme layouts, save it or send it to a friend.'
      },
      {
        icon: 'eye-off',
        title: 'Freecam and Cinematic Studio',
        description: 'Fly the camera away from your player, then place camera keyframes on a timeline and play smooth shots with cinema bars for your videos.'
      },
      {
        icon: 'users',
        title: 'Chat emoji',
        description: 'Type :sob: or use the emoji button in chat. Pretupid players see real emoji, everyone else sees your message without the codes.'
      },
      {
        icon: 'toggle',
        title: 'Emote wheel and hotkeys',
        description: 'Hold the wheel key, point, release. Or give your favourite emotes their own keys.'
      },
      {
        icon: 'server',
        title: 'Friends, chat and invites',
        description: 'See who is online and where, chat, and invite friends to your server or singleplayer world.'
      },
      {
        icon: 'shield',
        title: 'Respects server rules',
        description: 'Modules a server does not allow pause on their own and turn back on when you leave. Server owners can list every module ID in game with /pretupid serverrules. Freecam is always allowed in singleplayer.'
      },
      {
        icon: 'copy',
        title: 'Skins in game',
        description: 'Keep a skin library, copy skins by username, and switch without leaving the game.'
      },
      {
        icon: 'refresh',
        title: 'Settings that follow you',
        description: 'Your client settings carry over to new instances and survive updates.'
      }
    ]
  }
]

export const highlightedFeatures: Feature[] = featureGroups.flatMap((g) => g.features.filter((f) => f.highlight))
