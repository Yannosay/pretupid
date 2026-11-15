import type { H3Event } from 'h3'

const encoder = new TextEncoder()
const keyCache = new Map<string, CryptoKey>()

export interface SignedToken {
  purpose: 'challenge' | 'session' | 'admin'
  sub: string
  name?: string
  serverId?: string
  epoch?: number
  exp: number
}

export function nowSeconds(): number {
  return Math.floor(Date.now() / 1000)
}

export function toHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export function randomHex(bytes = 16): string {
  const buffer = new Uint8Array(bytes)
  crypto.getRandomValues(buffer)
  return toHex(buffer)
}

export async function sha256Hex(data: Uint8Array | string): Promise<string> {
  const bytes = typeof data === 'string' ? encoder.encode(data) : new Uint8Array(data)
  return toHex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)))
}

export async function constantTimeEqual(a: string, b: string): Promise<boolean> {
  const [left, right] = await Promise.all([sha256Hex(a), sha256Hex(b)])
  let difference = 0
  for (let i = 0; i < left.length; i++) {
    difference |= left.charCodeAt(i) ^ right.charCodeAt(i)
  }
  return difference === 0
}

function base64url(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function fromBase64url(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

function signingSecret(event: H3Event, purpose: SignedToken['purpose']): string {
  const config = useRuntimeConfig(event)
  const value = purpose === 'admin' ? config.adminSessionSecret : config.presenceSecret
  if (typeof value !== 'string' || value.length < 32) {
    throw createError({ statusCode: 503, statusMessage: 'Service is not configured' })
  }
  return `${purpose}:${value}`
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  const cached = keyCache.get(secret)
  if (cached) {
    return cached
  }
  const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, [
    'sign',
    'verify'
  ])
  keyCache.set(secret, key)
  return key
}

export async function signToken(event: H3Event, payload: SignedToken): Promise<string> {
  const body = base64url(encoder.encode(JSON.stringify(payload)))
  const key = await hmacKey(signingSecret(event, payload.purpose))
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(body))
  return `${body}.${base64url(new Uint8Array(signature))}`
}

export async function verifyToken(event: H3Event, token: unknown, purpose: SignedToken['purpose']): Promise<SignedToken | null> {
  if (typeof token !== 'string' || token.length > 1024) {
    return null
  }
  const parts = token.split('.')
  if (parts.length !== 2 || !parts[0] || !parts[1]) {
    return null
  }
  try {
    const key = await hmacKey(signingSecret(event, purpose))
    const valid = await crypto.subtle.verify('HMAC', key, fromBase64url(parts[1]), encoder.encode(parts[0]))
    if (!valid) {
      return null
    }
    const payload = JSON.parse(new TextDecoder().decode(fromBase64url(parts[0]))) as SignedToken
    if (payload.purpose !== purpose || typeof payload.exp !== 'number' || payload.exp < nowSeconds() || typeof payload.sub !== 'string') {
      return null
    }
    return payload
  } catch {
    return null
  }
}

export function clientIp(event: H3Event): string {
  const header = getRequestHeader(event, 'cf-connecting-ip')
  if (header && header.length <= 64) {
    return header
  }
  return getRequestIP(event) ?? 'unknown'
}
