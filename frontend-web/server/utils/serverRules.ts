import type { H3Event } from 'h3'

export const CLIENT_MODULE_IDS = [
  'fps', 'cps', 'ping', 'coordinates', 'keystrokes', 'armor', 'potions', 'clock', 'direction', 'speed', 'memory', 'server',
  'session', 'reach', 'combo', 'saturation', 'item_counter', 'light_level', 'dynamic_fps', 'entity_culling',
  'particle_limiter', 'fps_boost', 'zoom', 'freelook', 'freecam', 'cinematic', 'no_hurt_cam', 'low_fire', 'clear_weather', 'motion_blur', 'fullbright',
  'crosshair', 'toggle_sprint', 'death_coordinates', 'durability_alert', 'chat_timestamps', 'chat_macros',
  'low_health_warning', 'tab_ping', 'own_nametag', 'biome', 'day', 'look_at', 'waypoints', 'compact_chat', 'nick_hider',
  'block_outline'
] as const

const PATTERN = /^(\*\.)?([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$/

export interface ServerRuleInput {
  pattern: string
  modules: string[]
  reason: string
}

export interface ServerRuleRow {
  id: number
  pattern: string
  modules: string
  reason: string
  created_at: number
  updated_at: number
}

export function parseServerRuleInput(raw: Record<string, unknown>): ServerRuleInput {
  const pattern = typeof raw.pattern === 'string' ? raw.pattern.trim().toLowerCase().replace(/\.$/, '') : ''
  if (pattern.length > 253 || !PATTERN.test(pattern)) {
    throw createError({ statusCode: 400, statusMessage: 'Use a server address like play.example.net or *.example.net' })
  }
  const allowed = new Set<string>(CLIENT_MODULE_IDS)
  const modules = Array.isArray(raw.modules) ? [...new Set(raw.modules.filter((m): m is string => typeof m === 'string' && allowed.has(m)))] : []
  if (modules.length === 0) {
    throw createError({ statusCode: 400, statusMessage: 'Pick at least one module to pause' })
  }
  const reason = typeof raw.reason === 'string' ? raw.reason.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, 160) : ''
  return { pattern, modules, reason }
}

export function presentServerRule(row: ServerRuleRow) {
  let modules: string[] = []
  try {
    const parsed = JSON.parse(row.modules)
    modules = Array.isArray(parsed) ? parsed.filter((m): m is string => typeof m === 'string') : []
  } catch {
    modules = []
  }
  return { id: row.id, pattern: row.pattern, modules, reason: row.reason, updatedAt: row.updated_at }
}

export async function listServerRules(event: H3Event) {
  const { results } = await db(event).prepare('SELECT * FROM server_rules ORDER BY pattern ASC LIMIT 500').all<ServerRuleRow>()
  return results.map(presentServerRule)
}
