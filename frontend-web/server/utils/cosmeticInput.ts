import type { H3Event } from 'h3'

const MAX_IMAGE_BYTES = 128 * 1024

export interface CosmeticInput {
  type: 'cape' | 'trail' | 'emote'
  name: string
  description: string
  data: string
  enabled: boolean
  sort: number
  image: Uint8Array | null
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

export function parseCosmeticInput(raw: Record<string, unknown>, requireImage: boolean): CosmeticInput {
  const type = raw.type === 'cape' || raw.type === 'trail' || raw.type === 'emote' ? raw.type : null
  if (!type) {
    throw createError({ statusCode: 400, statusMessage: 'type must be cape, trail or emote' })
  }
  const name = cleanSingleLine(raw.name, 40)
  if (!name) {
    throw createError({ statusCode: 400, statusMessage: 'Name is required' })
  }
  let data = '{}'
  if (type === 'trail') {
    data = JSON.stringify(parseTrailData(raw.data))
  } else if (type === 'emote') {
    data = JSON.stringify(parseEmoteData(raw.data))
    if (data.length > 32 * 1024) {
      throw createError({ statusCode: 400, statusMessage: 'Emote animation is too large' })
    }
  }
  const image = typeof raw.image === 'string' && raw.image.length > 0 ? decodeBase64(raw.image) : null
  if (type === 'cape' && requireImage && !image) {
    throw createError({ statusCode: 400, statusMessage: 'A cape needs a PNG image' })
  }
  const sort = typeof raw.sort === 'number' && Number.isFinite(raw.sort) ? Math.round(Math.min(9999, Math.max(-9999, raw.sort))) : 0
  return {
    type,
    name,
    description: cleanSingleLine(raw.description, 160),
    data,
    enabled: raw.enabled !== false,
    sort,
    image: type === 'cape' ? image : null
  }
}

export async function storeCosmeticImage(event: H3Event, id: string, image: Uint8Array): Promise<{ key: string; sha256: string }> {
  let sanitized: SanitizedPng
  try {
    sanitized = await sanitizePng(image, isCapeSize)
  } catch (error) {
    throw createError({ statusCode: 400, statusMessage: error instanceof Error ? error.message : 'Invalid image' })
  }
  const sha256 = await sha256Hex(sanitized.bytes)
  const key = `cosmetics/${id}-${sha256.slice(0, 16)}.png`
  await contentBucket(event).put(key, sanitized.bytes, {
    httpMetadata: { contentType: 'image/png', cacheControl: 'public, max-age=86400, immutable' }
  })
  return { key, sha256 }
}
