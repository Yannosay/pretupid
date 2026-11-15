export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `history:${player.uuid}`, 60, 60)
  const friend = routeParam(event, 'uuid')
  if (!isUuid(friend) || !(await areFriends(event, player.uuid, friend))) {
    throw createError({ statusCode: 404, statusMessage: 'Friend not found' })
  }
  const before = Number.parseInt(String(getQuery(event).before ?? ''), 10)
  const cursor = Number.isFinite(before) && before > 0 ? before : Number.MAX_SAFE_INTEGER
  const { results } = await db(event)
    .prepare(
      `SELECT id, from_uuid, to_uuid, kind, body, created_at, read_at FROM messages
       WHERE id < ?3 AND ((from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1))
       ORDER BY id DESC LIMIT 50`
    )
    .bind(player.uuid, friend, cursor)
    .all<{ id: number; from_uuid: string; to_uuid: string; kind: 'text' | 'invite'; body: string; created_at: number; read_at: number | null }>()
  await db(event)
    .prepare('UPDATE messages SET read_at = ? WHERE to_uuid = ? AND from_uuid = ? AND read_at IS NULL')
    .bind(nowSeconds(), player.uuid, friend)
    .run()
  return {
    messages: results.reverse().map((row) => ({
      id: row.id,
      from: row.from_uuid,
      to: row.to_uuid,
      kind: row.kind,
      body: row.kind === 'invite' ? parseJsonColumn(row.body, null) : row.body,
      at: row.created_at,
      read: row.read_at !== null
    }))
  }
})
