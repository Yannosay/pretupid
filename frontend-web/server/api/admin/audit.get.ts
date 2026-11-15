export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const { results } = await db(event)
    .prepare('SELECT id, action, target, detail, ip, created_at FROM audit_log ORDER BY id DESC LIMIT 200')
    .all()
  return { entries: results }
})
