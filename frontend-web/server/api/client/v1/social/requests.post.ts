const MAX_FRIENDS = 250

export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `friend-request:${player.uuid}`, 30, 3600)
  const body = await readJson<{ name: unknown }>(event)
  if (!isPlayerName(body.name)) {
    throw createError({ statusCode: 400, statusMessage: 'Enter a valid Minecraft name' })
  }
  const target = await findPlayerByName(event, body.name)
  if (!target || target.uuid === player.uuid) {
    throw createError({ statusCode: 404, statusMessage: 'No Pretupid player with that name' })
  }
  if (await isBlockedEitherWay(event, player.uuid, target.uuid)) {
    throw createError({ statusCode: 404, statusMessage: 'No Pretupid player with that name' })
  }
  if (await areFriends(event, player.uuid, target.uuid)) {
    return { status: 'friends', uuid: target.uuid, name: target.name }
  }
  const count = await db(event).prepare('SELECT COUNT(*) AS count FROM friendships WHERE uuid = ?').bind(player.uuid).first<{ count: number }>()
  if ((count?.count ?? 0) >= MAX_FRIENDS) {
    throw createError({ statusCode: 409, statusMessage: `You can have up to ${MAX_FRIENDS} friends` })
  }
  const now = nowSeconds()
  const reverse = await db(event)
    .prepare('SELECT 1 AS ok FROM friend_requests WHERE from_uuid = ? AND to_uuid = ?')
    .bind(target.uuid, player.uuid)
    .first()
  if (reverse) {
    await db(event).batch([
      db(event).prepare('DELETE FROM friend_requests WHERE (from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)').bind(player.uuid, target.uuid),
      db(event).prepare('INSERT OR IGNORE INTO friendships (uuid, friend, created_at) VALUES (?, ?, ?)').bind(player.uuid, target.uuid, now),
      db(event).prepare('INSERT OR IGNORE INTO friendships (uuid, friend, created_at) VALUES (?, ?, ?)').bind(target.uuid, player.uuid, now)
    ])
    pushToPlayer(event, target.uuid, { t: 'social' })
    return { status: 'friends', uuid: target.uuid, name: target.name }
  }
  if (!target.allow_requests) {
    throw createError({ statusCode: 403, statusMessage: `${target.name} is not accepting friend requests` })
  }
  const pending = await db(event).prepare('SELECT COUNT(*) AS count FROM friend_requests WHERE to_uuid = ?').bind(target.uuid).first<{ count: number }>()
  if ((pending?.count ?? 0) >= 100) {
    throw createError({ statusCode: 409, statusMessage: `${target.name} has too many pending requests` })
  }
  await db(event).prepare('INSERT OR IGNORE INTO friend_requests (from_uuid, to_uuid, created_at) VALUES (?, ?, ?)').bind(player.uuid, target.uuid, now).run()
  pushToPlayer(event, target.uuid, { t: 'social' })
  return { status: 'requested', uuid: target.uuid, name: target.name }
})
