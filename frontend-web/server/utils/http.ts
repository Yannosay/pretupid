import type { H3Event } from 'h3'

const DEFAULT_MAX_BODY = 16 * 1024

export function noStore(event: H3Event) {
  setResponseHeader(event, 'Cache-Control', 'no-store')
}

export async function readBytes(event: H3Event, maxBytes: number): Promise<Uint8Array> {
  const declared = Number(getRequestHeader(event, 'content-length') ?? '0')
  if (Number.isFinite(declared) && declared > maxBytes) {
    throw createError({ statusCode: 413, statusMessage: 'Payload too large' })
  }
  const body = await readRawBody(event, false)
  if (!body) {
    return new Uint8Array()
  }
  const bytes = body instanceof Uint8Array ? body : new Uint8Array(body as unknown as ArrayBuffer)
  if (bytes.byteLength > maxBytes) {
    throw createError({ statusCode: 413, statusMessage: 'Payload too large' })
  }
  return bytes
}

export async function readJson<T extends object>(event: H3Event, maxBytes = DEFAULT_MAX_BODY): Promise<Partial<T>> {
  const contentType = (getRequestHeader(event, 'content-type') ?? '').toLowerCase()
  if (!contentType.startsWith('application/json')) {
    throw createError({ statusCode: 415, statusMessage: 'Expected application/json' })
  }
  const raw = await readBytes(event, maxBytes)
  try {
    const parsed = JSON.parse(new TextDecoder().decode(raw)) as unknown
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('not an object')
    }
    return parsed as Partial<T>
  } catch {
    throw createError({ statusCode: 400, statusMessage: 'Expected a JSON object' })
  }
}

export function requireSameOrigin(event: H3Event) {
  const origin = getRequestHeader(event, 'origin')
  const host = getRequestHeader(event, 'host')
  if (!origin || !host) {
    throw createError({ statusCode: 403, statusMessage: 'Origin required' })
  }
  let originHost = ''
  try {
    originHost = new URL(origin).host
  } catch {
    originHost = ''
  }
  if (originHost !== host) {
    throw createError({ statusCode: 403, statusMessage: 'Cross-origin request rejected' })
  }
}

export function routeParam(event: H3Event, name: string): string {
  const value = getRouterParam(event, name)
  if (!value) {
    throw createError({ statusCode: 400, statusMessage: `Missing ${name}` })
  }
  return decodeURIComponent(value)
}
