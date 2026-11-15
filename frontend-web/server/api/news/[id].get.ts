export default defineEventHandler(async (event) => {
  const id = routeParam(event, 'id')
  if (!/^[a-z0-9-]{1,64}$/.test(id)) {
    throw createError({ statusCode: 404, statusMessage: 'Post not found' })
  }
  const row = await db(event).prepare('SELECT * FROM news WHERE id = ? AND published = 1').bind(id).first<NewsRow>()
  if (!row) {
    throw createError({ statusCode: 404, statusMessage: 'Post not found' })
  }
  setResponseHeader(event, 'Cache-Control', 'public, max-age=60')
  return presentNews(row, true)
})
