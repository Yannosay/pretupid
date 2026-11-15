export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const query = getQuery(event)
  const status = query.status === 'removed' ? 'removed' : 'active'
  const page = Math.max(0, Number.parseInt(String(query.page ?? '0'), 10) || 0)
  const order = status === 'active' ? 'c.reports DESC, c.created_at DESC' : 'c.removed_at DESC'
  const { results } = await db(event)
    .prepare(
      `SELECT c.id, c.uuid, c.sha256, c.width, c.height, c.size, c.status, c.reports, c.created_at, c.removed_at, c.removed_reason,
              p.name, p.upload_banned, p.banned
       FROM capes c LEFT JOIN players p ON p.uuid = c.uuid
       WHERE c.status = ? ORDER BY ${order} LIMIT 48 OFFSET ?`
    )
    .bind(status, page * 48)
    .all<Record<string, unknown> & { sha256: string }>()
  return {
    capes: results.map((row) => ({ ...row, preview: `/api/admin/capes/preview/${row.sha256}` })),
    page,
    hasMore: results.length === 48
  }
})
