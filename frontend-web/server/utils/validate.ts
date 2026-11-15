const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const NAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/
const COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/
const ID_PATTERN = /^[a-z0-9][a-z0-9_-]{0,31}$/
const SHA256_PATTERN = /^[0-9a-f]{64}$/
const ADDRESS_PATTERN = /^[A-Za-z0-9.\-:[\]_]{1,253}$/

export const COLOR_ANIMATIONS = ['NONE', 'STATIC', 'GRADIENT', 'WAVE', 'PULSE', 'SHIMMER', 'RAINBOW', 'FLAME', 'AURORA'] as const
export const MOTION_EFFECTS = ['NONE', 'WAVE', 'BOUNCE', 'FLOAT', 'SHAKE', 'HOP'] as const

export interface NametagStyle {
  badge: boolean
  animation: (typeof COLOR_ANIMATIONS)[number]
  effect: (typeof MOTION_EFFECTS)[number]
  primary: string
  secondary: string
  speed: number
  bold: boolean
}

export interface Equipped {
  cape: string | null
  trail: string | null
  hat: string | null
  boots: string | null
  pet: string | null
}

export const HAT_IDS = ['top_hat', 'crown', 'halo', 'party_hat', 'wizard_hat', 'cat_ears', 'propeller_cap', 'headphones'] as const
export const BOOT_IDS = ['sneakers', 'cowboy_boots', 'flame_boots', 'frost_boots', 'gold_boots'] as const
export const PET_IDS = ['parrot_red', 'parrot_blue', 'parrot_green', 'parrot_cyan', 'parrot_gray'] as const

function pickAllowed<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  return typeof value === 'string' && (allowed as readonly string[]).includes(value) ? (value as T) : null
}

export function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

export function isPlayerName(value: unknown): value is string {
  return typeof value === 'string' && NAME_PATTERN.test(value)
}

export function isCosmeticId(value: unknown): value is string {
  return typeof value === 'string' && ID_PATTERN.test(value)
}

export function isSha256(value: unknown): value is string {
  return typeof value === 'string' && SHA256_PATTERN.test(value)
}

export function dashedUuid(compact: string): string | null {
  if (!/^[0-9a-f]{32}$/.test(compact)) {
    return null
  }
  return `${compact.slice(0, 8)}-${compact.slice(8, 12)}-${compact.slice(12, 16)}-${compact.slice(16, 20)}-${compact.slice(20)}`
}

function pick<T extends readonly string[]>(values: T, value: unknown, fallback: T[number]): T[number] {
  return typeof value === 'string' && (values as readonly string[]).includes(value) ? (value as T[number]) : fallback
}

function color(value: unknown, fallback: string): string {
  return typeof value === 'string' && COLOR_PATTERN.test(value) ? value.toUpperCase() : fallback
}

export function sanitizeStyle(input: unknown): NametagStyle {
  const raw = input !== null && typeof input === 'object' ? (input as Record<string, unknown>) : {}
  const speed = typeof raw.speed === 'number' && Number.isFinite(raw.speed) ? Math.min(3, Math.max(0.1, Math.round(raw.speed * 100) / 100)) : 1
  return {
    badge: raw.badge !== false,
    animation: pick(COLOR_ANIMATIONS, raw.animation, 'NONE'),
    effect: pick(MOTION_EFFECTS, raw.effect, 'NONE'),
    primary: color(raw.primary, '#FFFFFF'),
    secondary: color(raw.secondary, '#FFFFFF'),
    speed,
    bold: raw.bold === true
  }
}

export function sanitizeEquipped(input: unknown): Equipped {
  const raw = input !== null && typeof input === 'object' ? (input as Record<string, unknown>) : {}
  let cape: string | null = null
  if (raw.cape === 'custom') {
    cape = 'custom'
  } else if (typeof raw.cape === 'string' && raw.cape.startsWith('catalog:') && isCosmeticId(raw.cape.slice(8))) {
    cape = raw.cape
  }
  const trail = isCosmeticId(raw.trail) ? raw.trail : null
  return { cape, trail, hat: pickAllowed(raw.hat, HAT_IDS), boots: pickAllowed(raw.boots, BOOT_IDS), pet: pickAllowed(raw.pet, PET_IDS) }
}

export function parseJsonColumn<T>(value: unknown, fallback: T): T {
  if (typeof value !== 'string' || value.length === 0) {
    return fallback
  }
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

export function cleanText(value: unknown, maxLength: number): string {
  if (typeof value !== 'string') {
    return ''
  }
  return value
    .normalize('NFC')
    .replace(/§/g, '')
    .replace(/[\u0000-\u0008\u000B-\u001F\u007F-\u009F​-‏‪-‮⁦-⁩]/g, '')
    .trim()
    .slice(0, maxLength)
}

export function cleanSingleLine(value: unknown, maxLength: number): string {
  return cleanText(value, maxLength * 2)
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .slice(0, maxLength)
}

export function cleanServerAddress(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim().toLowerCase()
  if (!ADDRESS_PATTERN.test(trimmed)) {
    return null
  }
  return trimmed
}
