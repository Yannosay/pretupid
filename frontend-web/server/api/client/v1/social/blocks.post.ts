export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `block:${player.uuid}`, 60, 3600)
  const body = await readJson<{ uuid: unknown; blocked: unknown }>(event)
  if (!isUuid(body.uuid) || body.uuid === player.uuid) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid player' })
  }
  if (body.blocked === false) {
    await db(event).prepare('DELETE FROM blocks WHERE uuid = ? AND blocked = ?').bind(player.uuid, body.uuid).run()
    return { ok: true, blocked: false }
  }
  await db(event).batch([
    db(event).prepare('INSERT OR IGNORE INTO blocks (uuid, blocked, created_at) VALUES (?, ?, ?)').bind(player.uuid, body.uuid, nowSeconds()),
    db(event).prepare('DELETE FROM friendships WHERE (uuid = ?1 AND friend = ?2) OR (uuid = ?2 AND friend = ?1)').bind(player.uuid, body.uuid),
    db(event).prepare('DELETE FROM friend_requests WHERE (from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)').bind(player.uuid, body.uuid)
  ])
  return { ok: true, blocked: true }
})
