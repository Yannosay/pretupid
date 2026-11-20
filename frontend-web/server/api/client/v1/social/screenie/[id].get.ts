export default defineEventHandler(async (event) => {
  const player = await requirePlayer(event)
  const id = routeParam(event, 'id')
  if (!/^[0-9a-f]{32}$/.test(id)) {
    throw createError({ statusCode: 404, statusMessage: 'Screenie not found' })
  }
  const row = await db(event)
    .prepare('SELECT id FROM screenies WHERE id = ?1 AND (owner = ?2 OR recipient = ?2)')
    .bind(id, player.uuid)
    .first<{ id: string }>()
  const object = row ? await contentBucket(event).get(`screenies/${id}.png`) : null
  if (!object) {
    throw createError({ statusCode: 404, statusMessage: 'Screenie not found' })
  }
  return serveImage(event, object, 'private, max-age=86400')
})
