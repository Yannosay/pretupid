export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const { results } = await db(event).prepare('SELECT * FROM news ORDER BY updated_at DESC LIMIT 200').all<NewsRow>()
  return {
    posts: results.map((row) => ({ ...presentNews(row, true), published: row.published === 1, createdAt: row.created_at }))
  }
})
