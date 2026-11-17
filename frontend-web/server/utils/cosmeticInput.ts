import type { H3Event } from 'h3'

const MAX_IMAGE_BYTES = 128 * 1024

export const COSMETIC_TYPES = ['cape', 'trail', 'emote', ...WEARABLE_KINDS] as const
export type CosmeticType = (typeof COSMETIC_TYPES)[number]

export interface CosmeticInput {
  type: CosmeticType
  name: string
  description: string
  rawData: unknown
  enabled: boolean
  sort: number
  image: Uint8Array | null
  removeImage: boolean
}

export interface ExistingCosmetic {
  type: string
  asset_key: string | null
  asset_sha256: string | null
  data: string
}

export interface StoredCosmetic {
  data: string
  assetKey: string | null
  assetSha: string | null
  replacedKey: string | null
}

function decodeBase64(value: string): Uint8Array {
  const clean = value.replace(/^data:image\/png;base64,/, '')
  if (!/^[A-Za-z0-9+/=]+$/.test(clean) || clean.length > Math.ceil((MAX_IMAGE_BYTES * 4) / 3) + 4) {
    throw createError({ statusCode: 400, statusMessage: 'Image must be a PNG up to 128 KB' })
  }
  const binary = atob(clean)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

export function isCosmeticType(value: unknown): value is CosmeticType {
  return typeof value === 'string' && (COSMETIC_TYPES as readonly string[]).includes(value)
}

export function parseCosmeticInput(raw: Record<string, unknown>): CosmeticInput {
  if (!isCosmeticType(raw.type)) {
    throw createError({ statusCode: 400, statusMessage: `type must be one of ${COSMETIC_TYPES.join(', ')}` })
  }
  const name = cleanSingleLine(raw.name, 40)
  if (!name) {
    throw createError({ statusCode: 400, statusMessage: 'Name is required' })
  }
  const image = typeof raw.image === 'string' && raw.image.length > 0 ? decodeBase64(raw.image) : null
  const sort = typeof raw.sort === 'number' && Number.isFinite(raw.sort) ? Math.round(Math.min(9999, Math.max(-9999, raw.sort))) : 0
  return {
    type: raw.type,
    name,
    description: cleanSingleLine(raw.description, 160),
    rawData: raw.data,
    enabled: raw.enabled !== false,
    sort,
    image: raw.type === 'trail' || raw.type === 'emote' ? null : image,
    removeImage: raw.removeImage === true && isWearableKind(raw.type)
  }
}

async function storeImage(event: H3Event, id: string, image: Uint8Array, wearable: boolean): Promise<{ key: string; sha256: string; width: number; height: number }> {
  let sanitized: SanitizedPng
  try {
    sanitized = await sanitizePng(image, wearable ? isWearableTextureSize : isCapeSize)
  } catch (error) {
    const hint = wearable ? 'Textures must be PNGs with sides of 16, 32, 64, 128 or 256 pixels' : 'Invalid image'
    throw createError({ statusCode: 400, statusMessage: error instanceof Error ? `${error.message}. ${hint}` : hint })
  }
  const sha256 = await sha256Hex(sanitized.bytes)
  const key = `cosmetics/${id}-${sha256.slice(0, 16)}.png`
  await contentBucket(event).put(key, sanitized.bytes, {
    httpMetadata: { contentType: 'image/png', cacheControl: 'public, max-age=86400, immutable' }
  })
  return { key, sha256, width: sanitized.width, height: sanitized.height }
}

function existingTexture(existing: ExistingCosmetic | null): { width: number; height: number } | null {
  if (!existing?.asset_key) {
    return null
  }
  const model = parseJsonColumn<{ textureWidth?: unknown; textureHeight?: unknown }>(existing.data, {})
  return typeof model.textureWidth === 'number' && typeof model.textureHeight === 'number'
    ? { width: model.textureWidth, height: model.textureHeight }
    : null
}

export async function storeCosmetic(event: H3Event, id: string, input: CosmeticInput, existing: ExistingCosmetic | null): Promise<StoredCosmetic> {
  const wearable = isWearableKind(input.type)
  let assetKey = existing?.asset_key ?? null
  let assetSha = existing?.asset_sha256 ?? null
  let replacedKey: string | null = null
  let texture = wearable ? existingTexture(existing) : null
  if (input.type === 'cape' && !input.image && !assetKey) {
    throw createError({ statusCode: 400, statusMessage: 'A cape needs a PNG image' })
  }
  let data = '{}'
  if (input.type === 'trail') {
    data = JSON.stringify(parseTrailData(input.rawData))
  } else if (input.type === 'emote') {
    data = JSON.stringify(parseEmoteData(input.rawData))
    if (data.length > 32 * 1024) {
      throw createError({ statusCode: 400, statusMessage: 'Emote animation is too large' })
    }
  } else if (wearable) {
    if (input.removeImage) {
      texture = null
    }
    if (input.image) {
      const probe = await sanitizePng(input.image, isWearableTextureSize).catch(() => null)
      if (probe) {
        texture = { width: probe.width, height: probe.height }
      }
    }
    data = JSON.stringify(parseWearableModel(input.rawData, texture))
    if (data.length > 64 * 1024) {
      throw createError({ statusCode: 400, statusMessage: 'Wearable model is too large' })
    }
  }
  if (input.image) {
    const asset = await storeImage(event, id, input.image, wearable)
    if (assetKey && assetKey !== asset.key) {
      replacedKey = assetKey
    }
    assetKey = asset.key
    assetSha = asset.sha256
  } else if (input.removeImage && assetKey) {
    replacedKey = assetKey
    assetKey = null
    assetSha = null
  }
  return { data, assetKey, assetSha, replacedKey }
}
