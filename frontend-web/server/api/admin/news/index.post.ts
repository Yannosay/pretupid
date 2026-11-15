export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const input = parseNewsInput(await readJson<Record<string, unknown>>(event, 64 * 1024))
  const now = nowSeconds()
  const id = newsSlug(input.title)
  await db(event)
    .prepare(
      `INSERT INTO news (id, title, summary, body, category, pinned, published, published_at, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    )
    .bind(id, input.title, input.summary, input.body, input.category, input.pinned ? 1 : 0, input.published ? 1 : 0, input.published ? now : null, now, now)
    .run()
  await audit(event, 'news.create', id, input.title)
  return { id }
})
