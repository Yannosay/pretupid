export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = routeParam(event, 'id')
  const existing = await db(event).prepare('SELECT asset_key FROM cosmetics WHERE id = ?').bind(id).first<{ asset_key: string | null }>()
  if (!existing) {
    throw createError({ statusCode: 404, statusMessage: 'Cosmetic not found' })
  }
  await db(event).prepare('DELETE FROM cosmetics WHERE id = ?').bind(id).run()
  if (existing.asset_key) {
    await contentBucket(event).delete(existing.asset_key)
  }
  await audit(event, 'cosmetic.delete', id)
  setResponseStatus(event, 204)
  return null
})
