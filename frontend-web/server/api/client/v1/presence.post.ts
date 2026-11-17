export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `presence:${player.uuid}`, 12, 60)
  const body = await readJson<{
    style: unknown
    equipped: unknown
    clientVersion: unknown
    shareServer: unknown
    allowRequests: unknown
    server: unknown
  }>(event)
  const style = sanitizeStyle(body.style)
  const equipped = sanitizeEquipped(body.equipped)
  const clientVersion = cleanSingleLine(body.clientVersion, 32) || 'unknown'
  const shareServer = body.shareServer === true
  const allowRequests = body.allowRequests !== false
  const server = shareServer ? cleanServerAddress(body.server) : null
  const now = nowSeconds()
  await db(event)
    .prepare(
      `UPDATE players SET name = ?2, name_lower = ?3, last_seen = ?4, online = 1, client_version = ?5, style = ?6, equipped = ?7,
       server_address = ?8, share_server = ?9, allow_requests = ?10 WHERE uuid = ?1`
    )
    .bind(
      player.uuid,
      player.name,
      player.name.toLowerCase(),
      now,
      clientVersion,
      JSON.stringify(style),
      JSON.stringify(equipped),
      server,
      shareServer ? 1 : 0,
      allowRequests ? 1 : 0
    )
    .run()
  const [online, catalog] = await Promise.all([
    db(event)
      .prepare('SELECT COUNT(*) AS count FROM players WHERE online = 1 AND last_seen >= ?')
      .bind(now - PRESENCE_TTL_SECONDS)
      .first<{ count: number }>(),
    db(event)
      .prepare('SELECT COALESCE(MAX(updated_at), 0) AS version, COUNT(*) AS count FROM cosmetics WHERE enabled = 1')
      .first<{ version: number; count: number }>()
  ])
  if (Math.random() < 0.02) {
    event.waitUntil(
      Promise.all([
        pruneRateLimits(event),
        db(event).prepare('DELETE FROM messages WHERE created_at < ?').bind(now - 30 * 86_400).run(),
        db(event).prepare('UPDATE players SET online = 0 WHERE online = 1 AND last_seen < ?').bind(now - PRESENCE_TTL_SECONDS).run()
      ]).catch(() => undefined)
    )
  }
  return {
    nextHeartbeatSeconds: HEARTBEAT_SECONDS,
    online: online?.count ?? 0,
    catalogVersion: `${catalog?.version ?? 0}-${catalog?.count ?? 0}`
  }
})
