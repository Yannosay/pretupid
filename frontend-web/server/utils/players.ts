import type { H3Event } from 'h3'

export const PRESENCE_TTL_SECONDS = 180
export const HEARTBEAT_SECONDS = 60
export const EMOTE_WINDOW_SECONDS = 15
export const MAX_LOOKUP = 90

export interface PlayerRow {
  uuid: string
  name: string
  last_seen: number
  online: number
  style: string
  equipped: string
  emote_id: string | null
  emote_at: number | null
  server_address: string | null
  share_server: number
  allow_requests: number
  upload_banned: number
}

export interface PublicProfile {
  style: NametagStyle
  cape: { url: string; sha256: string } | null
  trail: string | null
  hat: string | null
  boots: string | null
  pet: string | null
  emote: { id: string; age: number } | null
}

export function isOnline(row: Pick<PlayerRow, 'online' | 'last_seen'>, now = nowSeconds()): boolean {
  return row.online === 1 && row.last_seen >= now - PRESENCE_TTL_SECONDS
}

export async function activeCapeHashes(event: H3Event, uuids: string[]): Promise<Map<string, string>> {
  const result = new Map<string, string>()
  if (uuids.length === 0) {
    return result
  }
  const placeholders = uuids.map(() => '?').join(',')
  const { results } = await db(event)
    .prepare(`SELECT uuid, sha256 FROM capes WHERE status = 'active' AND uuid IN (${placeholders})`)
    .bind(...uuids)
    .all<{ uuid: string; sha256: string }>()
  for (const row of results) {
    result.set(row.uuid, row.sha256)
  }
  return result
}

export async function publicProfiles(event: H3Event, uuids: string[]): Promise<Record<string, PublicProfile>> {
  const unique = [...new Set(uuids.filter(isUuid))].slice(0, MAX_LOOKUP)
  const profiles: Record<string, PublicProfile> = {}
  if (unique.length === 0) {
    return profiles
  }
  const now = nowSeconds()
  const placeholders = unique.map(() => '?').join(',')
  const { results } = await db(event)
    .prepare(
      `SELECT uuid, name, last_seen, online, style, equipped, emote_id, emote_at FROM players
       WHERE banned = 0 AND online = 1 AND last_seen >= ? AND uuid IN (${placeholders})`
    )
    .bind(now - PRESENCE_TTL_SECONDS, ...unique)
    .all<PlayerRow>()
  const customCapeOwners = results
    .filter((row) => parseJsonColumn<Partial<Equipped>>(row.equipped, {}).cape === 'custom')
    .map((row) => row.uuid)
  const [customCapes, catalog] = await Promise.all([activeCapeHashes(event, customCapeOwners), loadCatalog(event)])
  const catalogCapes = new Map(catalog.capes.map((cape) => [cape.id, cape]))
  const trails = new Set(catalog.trails.map((trail) => trail.id))
  const emotes = new Set(catalog.emotes.map((emote) => emote.id))
  for (const row of results) {
    const equipped = sanitizeEquipped(parseJsonColumn(row.equipped, {}))
    let cape: PublicProfile['cape'] = null
    if (equipped.cape === 'custom') {
      const sha = customCapes.get(row.uuid)
      cape = sha ? { url: capeUrl(event, sha), sha256: sha } : null
    } else if (equipped.cape?.startsWith('catalog:')) {
      const entry = catalogCapes.get(equipped.cape.slice(8))
      cape = entry ? { url: entry.url, sha256: entry.sha256 } : null
    }
    const emoteAge = row.emote_at ? now - row.emote_at : Number.POSITIVE_INFINITY
    profiles[row.uuid] = {
      style: sanitizeStyle(parseJsonColumn(row.style, {})),
      cape,
      trail: equipped.trail && trails.has(equipped.trail) ? equipped.trail : null,
      hat: equipped.hat,
      boots: equipped.boots,
      pet: equipped.pet,
      emote: row.emote_id && emotes.has(row.emote_id) && emoteAge <= EMOTE_WINDOW_SECONDS ? { id: row.emote_id, age: emoteAge } : null
    }
  }
  return profiles
}

export async function findPlayerByName(event: H3Event, name: string): Promise<{ uuid: string; name: string; allow_requests: number } | null> {
  return db(event)
    .prepare('SELECT uuid, name, allow_requests FROM players WHERE name_lower = ? AND banned = 0 ORDER BY last_seen DESC LIMIT 1')
    .bind(name.toLowerCase())
    .first<{ uuid: string; name: string; allow_requests: number }>()
}

export async function areFriends(event: H3Event, a: string, b: string): Promise<boolean> {
  const row = await db(event).prepare('SELECT 1 AS ok FROM friendships WHERE uuid = ? AND friend = ?').bind(a, b).first<{ ok: number }>()
  return row !== null
}

export async function isBlockedEitherWay(event: H3Event, a: string, b: string): Promise<boolean> {
  const row = await db(event)
    .prepare('SELECT 1 AS ok FROM blocks WHERE (uuid = ?1 AND blocked = ?2) OR (uuid = ?2 AND blocked = ?1) LIMIT 1')
    .bind(a, b)
    .first<{ ok: number }>()
  return row !== null
}
