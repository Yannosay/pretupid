export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = Number(getRouterParam(event, 'id'))
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid rule' })
  }
  const row = await db(event).prepare('SELECT pattern FROM server_rules WHERE id = ?').bind(id).first<{ pattern: string }>()
  if (!row) {
    throw createError({ statusCode: 404, statusMessage: 'Rule not found' })
  }
  await db(event).prepare('DELETE FROM server_rules WHERE id = ?').bind(id).run()
  await audit(event, 'server_rule.delete', row.pattern)
  return { ok: true }
})
