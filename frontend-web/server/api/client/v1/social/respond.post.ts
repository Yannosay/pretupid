export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `friend-respond:${player.uuid}`, 60, 3600)
  const body = await readJson<{ uuid: unknown; accept: unknown }>(event)
  if (!isUuid(body.uuid)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid player' })
  }
  const request = await db(event)
    .prepare('SELECT 1 AS ok FROM friend_requests WHERE from_uuid = ? AND to_uuid = ?')
    .bind(body.uuid, player.uuid)
    .first()
  if (!request) {
    throw createError({ statusCode: 404, statusMessage: 'Friend request not found' })
  }
  const statements = [db(event).prepare('DELETE FROM friend_requests WHERE from_uuid = ? AND to_uuid = ?').bind(body.uuid, player.uuid)]
  if (body.accept === true) {
    const now = nowSeconds()
    statements.push(
      db(event).prepare('DELETE FROM friend_requests WHERE from_uuid = ? AND to_uuid = ?').bind(player.uuid, body.uuid),
      db(event).prepare('INSERT OR IGNORE INTO friendships (uuid, friend, created_at) VALUES (?, ?, ?)').bind(player.uuid, body.uuid, now),
      db(event).prepare('INSERT OR IGNORE INTO friendships (uuid, friend, created_at) VALUES (?, ?, ?)').bind(body.uuid, player.uuid, now)
    )
  }
  await db(event).batch(statements)
  pushToPlayer(event, body.uuid, { t: 'social' })
  return { ok: true, accepted: body.accept === true }
})
