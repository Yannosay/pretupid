import { PLAYER_CERTIFICATE_KEYS, PROFILE_PROPERTY_KEYS } from './mojangKeys'

const encoder = new TextEncoder()
const MAX_TEXTURES_AGE_MS = 24 * 60 * 60 * 1000
const MAX_KEY_BYTES = 1024
const MAX_SIGNATURE_BYTES = 1024

export interface ProfileKeyProof {
  publicKey: string
  expiresAt: number
  keySignature: string
  signature: string
}

export interface SignedTextures {
  value: string
  signature: string
}

export function authPayload(serverId: string, uuid: string): Uint8Array<ArrayBuffer> {
  return encoder.encode(`pretupid-auth:${serverId}:${uuid}`)
}

function decodeBase64(value: unknown, maxBytes: number): Uint8Array<ArrayBuffer> | null {
  if (typeof value !== 'string' || value.length === 0 || value.length > Math.ceil((maxBytes * 4) / 3) + 4) {
    return null
  }
  try {
    const binary = atob(value)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }
    return bytes
  } catch {
    return null
  }
}

const trustedKeyCache = new Map<string, Promise<CryptoKey>>()

function importRsaKey(spki: Uint8Array<ArrayBuffer>, hash: 'SHA-1' | 'SHA-256'): Promise<CryptoKey> {
  return crypto.subtle.importKey('spki', spki, { name: 'RSASSA-PKCS1-v1_5', hash }, false, ['verify'])
}

function trustedKey(base64: string): Promise<CryptoKey> {
  let key = trustedKeyCache.get(base64)
  if (!key) {
    key = importRsaKey(decodeBase64(base64, 4096)!, 'SHA-1')
    trustedKeyCache.set(base64, key)
  }
  return key
}

async function signedByAny(keys: readonly string[], signature: Uint8Array<ArrayBuffer>, data: Uint8Array<ArrayBuffer>): Promise<boolean> {
  for (const base64 of keys) {
    if (await crypto.subtle.verify('RSASSA-PKCS1-v1_5', await trustedKey(base64), signature, data)) {
      return true
    }
  }
  return false
}

function uuidBytes(uuid: string): Uint8Array {
  const hex = uuid.replace(/-/g, '')
  const bytes = new Uint8Array(16)
  for (let i = 0; i < 16; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16)
  }
  return bytes
}

function certificatePayload(uuid: string, expiresAt: number, publicKey: Uint8Array): Uint8Array<ArrayBuffer> {
  const payload = new Uint8Array(24 + publicKey.length)
  payload.set(uuidBytes(uuid), 0)
  new DataView(payload.buffer).setBigInt64(16, BigInt(expiresAt))
  payload.set(publicKey, 24)
  return payload
}

export async function verifyProfileKey(uuid: string, serverId: string, proof: Partial<ProfileKeyProof> | undefined): Promise<boolean> {
  if (!proof || typeof proof.expiresAt !== 'number' || !Number.isSafeInteger(proof.expiresAt) || proof.expiresAt < Date.now()) {
    return false
  }
  const publicKey = decodeBase64(proof.publicKey, MAX_KEY_BYTES)
  const keySignature = decodeBase64(proof.keySignature, MAX_SIGNATURE_BYTES)
  const signature = decodeBase64(proof.signature, MAX_SIGNATURE_BYTES)
  if (!publicKey || !keySignature || !signature) {
    return false
  }
  try {
    if (!(await signedByAny(PLAYER_CERTIFICATE_KEYS, keySignature, certificatePayload(uuid, proof.expiresAt, publicKey)))) {
      return false
    }
    const playerKey = await importRsaKey(publicKey, 'SHA-256')
    return await crypto.subtle.verify('RSASSA-PKCS1-v1_5', playerKey, signature, authPayload(serverId, uuid))
  } catch {
    return false
  }
}

export async function verifiedProfileName(uuid: string, textures: Partial<SignedTextures> | undefined): Promise<string | null> {
  if (!textures || typeof textures.value !== 'string' || textures.value.length > 8192) {
    return null
  }
  const signature = decodeBase64(textures.signature, MAX_SIGNATURE_BYTES)
  if (!signature) {
    return null
  }
  try {
    if (!(await signedByAny(PROFILE_PROPERTY_KEYS, signature, encoder.encode(textures.value)))) {
      return null
    }
    const decoded = JSON.parse(atob(textures.value)) as { timestamp?: unknown; profileId?: unknown; profileName?: unknown }
    if (typeof decoded.timestamp !== 'number' || Math.abs(Date.now() - decoded.timestamp) > MAX_TEXTURES_AGE_MS) {
      return null
    }
    if (typeof decoded.profileId !== 'string' || dashedUuid(decoded.profileId.toLowerCase()) !== uuid) {
      return null
    }
    return isPlayerName(decoded.profileName) ? decoded.profileName : null
  } catch {
    return null
  }
}
