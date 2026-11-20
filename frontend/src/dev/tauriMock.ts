import { mockIPC, mockWindows } from '@tauri-apps/api/mocks'
import type { Instance, InstanceMod, InstanceSettings } from '@/types'
import type { SkinSlotDto } from '@/types/profile'

const PROFILE = { id: 'c06f89064c8a49119c29ea1dbd1aab82', name: 'Steve' }
const STEVE = 'https://mc-heads.net/skin/MHF_Steve'
const MODRINTH = 'https://api.modrinth.com/v2'

function iso(daysAgo: number): string {
  return new Date(Date.now() - daysAgo * 86_400_000).toISOString()
}

function instance(id: string, name: string, version: string, loader: Instance['mod_loader'], played: number | null, hours: number): Instance {
  return {
    id,
    name,
    description: '',
    minecraft_version: version,
    mod_loader: loader,
    loader_version: loader === 'vanilla' ? null : '0.17.2',
    created_at: iso(40),
    updated_at: iso(2),
    last_played: played === null ? null : iso(played),
    playtime_seconds: hours * 3600,
    icon_url: null,
    group_name: null
  }
}

const instances: Instance[] = [
  instance('0b5f4c2e-1d9a-4f53-9a51-3d1c2b7e8f01', 'Survival with friends', '26.3', 'fabric', 0.1, 42),
  instance('1c6a5d3f-2eab-4064-8b62-4e2d3c8f9a12', 'Skyblock Speedrun', '26.3', 'fabric', 2, 9),
  instance('2d7b6e40-3fbc-4175-9c73-5f3e4d9a0b23', 'Vanilla', '26.3', 'vanilla', 6, 3),
  instance('3e8c7f51-40cd-4286-8d84-604f5e0b1c34', 'Create: Above and Beyond', '1.21.1', 'neoforge', 13, 61),
  instance('4f9d8062-51de-4397-9e95-71506f1c2d45', 'Test Instance With A Remarkably Long Name', '1.20.1', 'forge', null, 0)
]

function capeDataUrl(base: string, accent: string): string | null {
  if (typeof document === 'undefined') return null
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 32
  const context = canvas.getContext('2d')
  if (!context) return null
  context.fillStyle = base
  context.fillRect(0, 0, 22, 17)
  context.fillStyle = accent
  context.fillRect(1, 1, 10, 2)
  context.fillRect(4, 6, 4, 6)
  context.fillRect(1, 15, 10, 2)
  return canvas.toDataURL('image/png')
}

const mockCapes = [
  { id: 'migrator-cape', alias: 'Migrator', active: true, data_url: capeDataUrl('#1f3c88', '#f4c542') },
  { id: 'vanilla-cape', alias: 'Vanilla', active: false, data_url: capeDataUrl('#3f7f3f', '#e6e6e6') },
  { id: 'pan-cape', alias: 'Pan', active: false, data_url: capeDataUrl('#5a3a22', '#f2a541') }
]

const mods = new Map<string, InstanceMod[]>()

const settingsByInstance = new Map<string, InstanceSettings>()

function defaultInstanceSettings(): InstanceSettings {
  return {
    memory_min_mb: 1024,
    memory_max_mb: 4096,
    resolution_width: 1280,
    resolution_height: 720,
    fullscreen: false,
    java_path: null,
    extra_jvm_args: [],
    extra_game_args: [],
    quick_play_server: null
  }
}

const skinSlots: SkinSlotDto[] = [
  {
    id: 'slot-1',
    profile_id: PROFILE.id,
    slot_number: 1,
    name: 'Steve',
    file_name: 'steve.png',
    original_file_name: null,
    model_type: 'classic',
    source: 'url',
    is_active: true,
    created_at: iso(10),
    updated_at: iso(10),
    preview_path: STEVE,
    data_url: STEVE
  }
]

async function modrinth<T>(path: string, params: Record<string, string | number | null | undefined> = {}): Promise<T> {
  const url = new URL(MODRINTH + path)
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') url.searchParams.set(key, String(value))
  }
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Modrinth responded with ${response.status}`)
  return response.json() as Promise<T>
}

type Args = Record<string, unknown>

const MOCK_CLIENT_VERSION = '0.1.4'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const CLIENT_VERSIONS = new Set(['1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11', '26.1', '26.1.1', '26.1.2', '26.2', '26.3'])

function clientSupports(loader: Instance['mod_loader'], version: string): boolean {
  return (loader === 'fabric' || loader === 'quilt') && CLIENT_VERSIONS.has(version)
}

function mockMod(instanceId: string, projectId: string, title: string, version: string): InstanceMod {
  const entry: InstanceMod = {
    id: crypto.randomUUID(),
    instance_id: instanceId,
    project_id: projectId,
    project_type: 'mod',
    title,
    icon_url: null,
    version_id: version,
    version_name: version,
    file_url: null,
    file_name: `${projectId}-${version}.jar`,
    created_at: new Date().toISOString(),
    enabled: true,
    sha1: null
  }
  mods.set(instanceId, [...(mods.get(instanceId) ?? []), entry])
  return entry
}

const listeners = new Map<number, string>()
let nextListener = 1

let textureIndex: Promise<string[]> | null = null

function devTextureIndex(): Promise<string[]> {
  textureIndex ??= fetch('/__dev/block-textures/index.json')
    .then((response) => (response.ok ? (response.json() as Promise<string[]>) : []))
    .catch(() => [])
  return textureIndex
}

async function handle(cmd: string, args: Args): Promise<unknown> {
  switch (cmd) {
    case 'plugin:event|listen': {
      const id = nextListener++
      listeners.set(id, String(args.event))
      return id
    }
    case 'plugin:event|unlisten':
    case 'plugin:event|emit':
    case 'plugin:event|emit_to':
      return null
    case 'plugin:updater|check':
      return null
    case 'get_auth_status':
      return { authenticated: true, profile: PROFILE }
    case 'login_microsoft':
    case 'logout_microsoft':
      return null
    case 'take_pending_deep_links':
      return []
    case 'list_instances':
      return instances
    case 'get_instance':
      return instances.find((i) => i.id === args.id) ?? null
    case 'update_instance': {
      const target = instances.find((i) => i.id === args.id)
      if (!target) return null
      const request = args.request as Partial<Instance>
      for (const [key, value] of Object.entries(request)) {
        if (value !== null && value !== undefined) (target as unknown as Record<string, unknown>)[key] = value
      }
      return target
    }
    case 'create_instance': {
      const created = instance(crypto.randomUUID(), String(args.name), String(args.minecraftVersion), args.modLoader as Instance['mod_loader'], null, 0)
      instances.push(created)
      return created
    }
    case 'delete_instance': {
      const index = instances.findIndex((i) => i.id === args.id)
      if (index >= 0) instances.splice(index, 1)
      return index >= 0
    }
    case 'duplicate_instance': {
      const source = instances.find((i) => i.id === args.id)
      if (!source) throw new Error('Instance not found')
      const copy = { ...source, id: crypto.randomUUID(), name: `${source.name} (copy)` }
      instances.push(copy)
      return copy
    }
    case 'list_running_instances':
      return []
    case 'get_instance_settings':
      return settingsByInstance.get(String(args.id)) ?? defaultInstanceSettings()
    case 'save_instance_settings':
      settingsByInstance.set(String(args.id), args.settings as InstanceSettings)
      return null
    case 'get_system_info':
      return { total_memory_mb: 32768, recommended_memory_mb: 6144, cpu_threads: 16, os: 'windows' }
    case 'get_global_settings':
      return { default_java_path: null, auto_install_java: true, show_snapshots: false, auto_update_enabled: true }
    case 'save_global_settings':
      return null
    case 'get_app_info':
      return { name: 'Pretupid Launcher', version: '0.0.2-alpha' }
    case 'list_instance_mods':
      return mods.get(String(args.instanceId)) ?? []
    case 'check_instance_updates':
      return []
    case 'pretupid_client_status': {
      const target = instances.find((i) => i.id === args.instanceId)
      const installed = (mods.get(String(args.instanceId)) ?? []).find((entry) => entry.project_id === 'pretupid-client')
      const supported = !!target && clientSupports(target.mod_loader, target.minecraft_version)
      return {
        supported: supported || !!installed,
        installed: !!installed,
        enabled: installed?.enabled ?? false,
        mod_id: installed?.id ?? null,
        version_name: installed?.version_name ?? null,
        latest_version: supported ? MOCK_CLIENT_VERSION : null
      }
    }
    case 'pretupid_client_available':
      return CLIENT_VERSIONS.has(String(args.minecraftVersion))
    case 'install_pretupid_client': {
      await delay(1800)
      const entry = mockMod(String(args.instanceId), 'pretupid-client', 'Pretupid Client', MOCK_CLIENT_VERSION)
      return { installed: [entry], warnings: [] }
    }
    case 'install_performance_pack': {
      await delay(1200)
      const installed = ['Sodium', 'Lithium', 'FerriteCore'].map((title) => mockMod(String(args.instanceId), title.toLowerCase(), title, '1.0.0'))
      return { installed, warnings: [] }
    }
    case 'fetch_versions': {
      const manifest = await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json').then((r) => r.json())
      return (manifest.versions as { id: string; type: string; url: string; time: string; releaseTime: string; sha1: string }[]).map((v) => ({
        id: v.id,
        type: v.type,
        url: v.url,
        time: v.time,
        release_time: v.releaseTime,
        sha1: v.sha1
      }))
    }
    case 'get_loader_versions':
      return [{ version: '0.17.2', stable: true, recommended: true, latest: true }]
    case 'search_modrinth':
      return modrinth('/search', {
        query: args.query as string,
        facets: args.facets as string,
        index: args.index as string,
        offset: args.offset as number,
        limit: args.limit as number
      })
    case 'get_modrinth_project':
      return modrinth(`/project/${args.idOrSlug}`)
    case 'get_modrinth_versions':
      return modrinth(`/project/${args.idOrSlug}/version`)
    case 'get_modrinth_categories':
      return modrinth('/tag/category')
    case 'get_modrinth_loaders':
      return modrinth('/tag/loader')
    case 'get_modrinth_game_versions':
      return modrinth('/tag/game_version')
    case 'fetch_news':
      return [
        { id: 'welcome', title: 'Pretupid 0.1.1 is here', summary: 'Custom instance icons, a brand new home screen and a faster downloader.', category: 'update', pinned: true, publishedAt: Math.floor(Date.now() / 1000) - 86_400 },
        { id: 'cosmetics', title: 'Wings, pets and hats arrive', summary: 'Pretupid Client now has animated wings, floating pets and hats that every Pretupid player can see.', category: 'announcement', pinned: false, publishedAt: Math.floor(Date.now() / 1000) - 4 * 86_400 },
        { id: 'event', title: 'Build contest this weekend', summary: 'Show off your best base and win an exclusive cape.', category: 'event', pinned: false, publishedAt: Math.floor(Date.now() / 1000) - 9 * 86_400 }
      ]
    case 'fetch_news_post':
      return {
        id: args.id,
        title: 'Wings, pets and hats arrive',
        summary: 'Pretupid Client now has animated wings, floating pets and hats that every Pretupid player can see.',
        category: 'announcement',
        publishedAt: Math.floor(Date.now() / 1000) - 4 * 86_400,
        body: [
          '## Dress up',
          '',
          'Pretupid Client now has **animated wings**, floating pets and hats that every Pretupid player can see.',
          '',
          '- Pets follow you around',
          '- Hats sit on top of your skin layer',
          '- Wings flap while you fly',
          '',
          '> Open *Cosmetics* in game to try them.',
          '',
          '[[Read the full changelog]](https://github.com/Yannosay/pretupid/releases)'
        ].join(String.fromCharCode(10))
      }
    case 'get_block_textures': {
      const names = (args.names as string[]) ?? []
      const available = new Set(await devTextureIndex())
      return Object.fromEntries(names.filter((name) => available.has(name)).map((name) => [name, `/__dev/block-textures/${name}.png`]))
    }
    case 'list_block_textures':
      return devTextureIndex()
    case 'lookup_player_skin':
      return { url: `https://mc-heads.net/skin/${args.username}`, model_type: 'classic' }
    case 'list_skin_slots':
      return skinSlots
    case 'get_current_skin':
      return { preview_path: STEVE, model_type: 'classic', data_url: STEVE }
    case 'set_active_skin_slot':
      for (const slot of skinSlots) slot.is_active = slot.id === args.slotId
      return null
    case 'list_minecraft_capes':
      return mockCapes.map((cape) => ({ ...cape }))
    case 'set_minecraft_cape':
      for (const cape of mockCapes) cape.active = cape.id === args.capeId
      return mockCapes.map((cape) => ({ ...cape }))
    case 'apply_skin_slot':
    case 'open_instance_folder':
    case 'open_external_link':
      return null
    default:
      console.warn('[tauri-mock] unhandled command', cmd, args)
      return null
  }
}

export function installTauriMock() {
  mockWindows('main')
  mockIPC((cmd, args) => handle(cmd, (args ?? {}) as Args))
}
