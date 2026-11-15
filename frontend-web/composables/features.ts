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
        title: 'All four loaders',
        description:
          'Fabric, Quilt, Forge and NeoForge from Minecraft 1.12 to today. Forge is installed natively, without running a separate installer window.',
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
        description: 'Start any instance from the home screen, the library, the top bar or the system tray.',
        highlight: true
      },
      {
        icon: 'alert',
        title: 'Crash helper',
        description:
          'When the game crashes, Pretupid reads the log and tells you the likely cause: missing dependencies, wrong Java, memory or driver issues.',
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
        description: 'Your Microsoft session is encrypted on disk with a key kept in the Windows Credential Manager.'
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
      }
    ]
  }
]

export const highlightedFeatures: Feature[] = featureGroups.flatMap((g) => g.features.filter((f) => f.highlight))
