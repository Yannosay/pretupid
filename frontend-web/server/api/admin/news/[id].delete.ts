export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = routeParam(event, 'id')
  const result = await db(event).prepare('DELETE FROM news WHERE id = ?').bind(id).run()
  if (!result.meta.changes) {
    throw createError({ statusCode: 404, statusMessage: 'Post not found' })
  }
  await audit(event, 'news.delete', id)
  setResponseStatus(event, 204)
  return null
})
