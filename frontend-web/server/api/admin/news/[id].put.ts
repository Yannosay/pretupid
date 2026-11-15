export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = routeParam(event, 'id')
  const existing = await db(event).prepare('SELECT published, published_at FROM news WHERE id = ?').bind(id).first<{
    published: number
    published_at: number | null
  }>()
  if (!existing) {
    throw createError({ statusCode: 404, statusMessage: 'Post not found' })
  }
  const input = parseNewsInput(await readJson<Record<string, unknown>>(event, 64 * 1024))
  const now = nowSeconds()
  const publishedAt = input.published ? existing.published_at ?? now : existing.published_at
  await db(event)
    .prepare('UPDATE news SET title = ?, summary = ?, body = ?, category = ?, pinned = ?, published = ?, published_at = ?, updated_at = ? WHERE id = ?')
    .bind(input.title, input.summary, input.body, input.category, input.pinned ? 1 : 0, input.published ? 1 : 0, publishedAt, now, id)
    .run()
  await audit(event, 'news.update', id, input.title)
  return { id }
})
