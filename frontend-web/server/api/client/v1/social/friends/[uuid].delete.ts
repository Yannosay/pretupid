export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  const uuid = routeParam(event, 'uuid')
  if (!isUuid(uuid)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid player' })
  }
  await db(event).batch([
    db(event).prepare('DELETE FROM friendships WHERE (uuid = ?1 AND friend = ?2) OR (uuid = ?2 AND friend = ?1)').bind(player.uuid, uuid),
    db(event).prepare('DELETE FROM friend_requests WHERE (from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)').bind(player.uuid, uuid)
  ])
  pushToPlayer(event, uuid, { t: 'social' })
  setResponseStatus(event, 204)
  return null
})
