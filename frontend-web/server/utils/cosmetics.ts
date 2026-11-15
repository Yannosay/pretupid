import type { H3Event } from 'h3'

export const TRAIL_PARTICLES = [
  'dust',
  'end_rod',
  'flame',
  'soul_fire_flame',
  'heart',
  'note',
  'happy_villager',
  'cherry_leaves',
  'electric_spark',
  'enchant',
  'glow',
  'snowflake',
  'totem_of_undying',
  'witch',
  'bubble_pop',
  'small_flame',
  'wax_on',
  'scrape'
] as const

export const TRAIL_PATTERNS = ['stream', 'spiral', 'ring', 'helix'] as const
export const EMOTE_PARTS = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'] as const

export interface TrailData {
  particle: (typeof TRAIL_PARTICLES)[number]
  colors: string[]
  density: number
  pattern: (typeof TRAIL_PATTERNS)[number]
  size: number
}

export interface EmoteKeyframe {
  t: number
  x: number
  y: number
  z: number
}

export interface EmoteData {
  duration: number
  loop: boolean
  ease: 'linear' | 'smooth'
  parts: Partial<Record<(typeof EMOTE_PARTS)[number], EmoteKeyframe[]>>
}

function clamp(value: unknown, min: number, max: number, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback
}

export function parseTrailData(input: unknown): TrailData {
  const raw = input !== null && typeof input === 'object' ? (input as Record<string, unknown>) : {}
  const particle = TRAIL_PARTICLES.find((value) => value === raw.particle)
  if (!particle) {
    throw createError({ statusCode: 400, statusMessage: `particle must be one of ${TRAIL_PARTICLES.join(', ')}` })
  }
  const colors = Array.isArray(raw.colors)
    ? raw.colors.filter((value): value is string => typeof value === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value)).slice(0, 4).map((value) => value.toUpperCase())
    : []
  return {
    particle,
    colors: colors.length > 0 ? colors : ['#FFFFFF'],
    density: Math.round(clamp(raw.density, 1, 5, 2)),
    pattern: TRAIL_PATTERNS.find((value) => value === raw.pattern) ?? 'stream',
    size: Math.round(clamp(raw.size, 0.5, 2, 1) * 100) / 100
  }
}

export function parseEmoteData(input: unknown): EmoteData {
  const raw = input !== null && typeof input === 'object' ? (input as Record<string, unknown>) : {}
  const partsInput = raw.parts !== null && typeof raw.parts === 'object' ? (raw.parts as Record<string, unknown>) : {}
  const parts: EmoteData['parts'] = {}
  let frames = 0
  for (const part of EMOTE_PARTS) {
    const list = partsInput[part]
    if (!Array.isArray(list)) {
      continue
    }
    const keyframes = list
      .slice(0, 64)
      .filter((frame): frame is Record<string, unknown> => frame !== null && typeof frame === 'object')
      .map((frame) => ({
        t: Math.round(clamp(frame.t, 0, 1, 0) * 1000) / 1000,
        x: Math.round(clamp(frame.x, -360, 360, 0) * 10) / 10,
        y: Math.round(clamp(frame.y, -360, 360, 0) * 10) / 10,
        z: Math.round(clamp(frame.z, -360, 360, 0) * 10) / 10
      }))
      .sort((a, b) => a.t - b.t)
    if (keyframes.length > 0) {
      parts[part] = keyframes
      frames += keyframes.length
    }
  }
  if (frames === 0) {
    throw createError({ statusCode: 400, statusMessage: 'An emote needs keyframes for at least one body part' })
  }
  return {
    duration: Math.round(clamp(raw.duration, 0.25, 12, 2) * 100) / 100,
    loop: raw.loop === true,
    ease: raw.ease === 'linear' ? 'linear' : 'smooth',
    parts
  }
}

export function publicBase(event: H3Event): string {
  const configured = useRuntimeConfig(event).public.siteUrl
  if (typeof configured === 'string' && configured.startsWith('https://')) {
    return configured.replace(/\/+$/, '')
  }
  return getRequestURL(event).origin
}

export function capeUrl(event: H3Event, sha256: string): string {
  return `${publicBase(event)}/api/content/capes/${sha256}.png`
}

export function catalogAssetUrl(event: H3Event, key: string): string {
  return `${publicBase(event)}/api/content/${key}`
}

interface CatalogRow {
  id: string
  type: 'cape' | 'trail' | 'emote'
  name: string
  description: string
  asset_key: string | null
  asset_sha256: string | null
  data: string
  updated_at: number
}

export interface Catalog {
  version: number
  capes: { id: string; name: string; description: string; url: string; sha256: string }[]
  trails: ({ id: string; name: string; description: string } & TrailData)[]
  emotes: { id: string; name: string; description: string; animation: EmoteData }[]
}

export async function loadCatalog(event: H3Event): Promise<Catalog> {
  const { results } = await db(event)
    .prepare('SELECT id, type, name, description, asset_key, asset_sha256, data, updated_at FROM cosmetics WHERE enabled = 1 ORDER BY sort, name')
    .all<CatalogRow>()
  const catalog: Catalog = { version: 0, capes: [], trails: [], emotes: [] }
  for (const row of results) {
    catalog.version = Math.max(catalog.version, row.updated_at)
    const base = { id: row.id, name: row.name, description: row.description }
    if (row.type === 'cape' && row.asset_key && row.asset_sha256) {
      catalog.capes.push({ ...base, url: catalogAssetUrl(event, row.asset_key), sha256: row.asset_sha256 })
    } else if (row.type === 'trail') {
      catalog.trails.push({ ...base, ...parseJsonColumn<TrailData>(row.data, { particle: 'end_rod', colors: ['#FFFFFF'], density: 2, pattern: 'stream', size: 1 }) })
    } else if (row.type === 'emote') {
      catalog.emotes.push({ ...base, animation: parseJsonColumn<EmoteData>(row.data, { duration: 1, loop: false, ease: 'smooth', parts: {} }) })
    }
  }
  return catalog
}
