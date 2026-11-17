export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = routeParam(event, 'id')
  const existing = await db(event)
    .prepare('SELECT type, asset_key, asset_sha256, data FROM cosmetics WHERE id = ?')
    .bind(id)
    .first<ExistingCosmetic>()
  if (!existing) {
    throw createError({ statusCode: 404, statusMessage: 'Cosmetic not found' })
  }
  const raw = await readJson<Record<string, unknown>>(event, 256 * 1024)
  const input = parseCosmeticInput({ ...raw, type: existing.type })
  const stored = await storeCosmetic(event, id, input, existing)
  await db(event)
    .prepare('UPDATE cosmetics SET name = ?, description = ?, asset_key = ?, asset_sha256 = ?, data = ?, enabled = ?, sort = ?, updated_at = ? WHERE id = ?')
    .bind(input.name, input.description, stored.assetKey, stored.assetSha, stored.data, input.enabled ? 1 : 0, input.sort, nowSeconds(), id)
    .run()
  if (stored.replacedKey) {
    await contentBucket(event).delete(stored.replacedKey)
  }
  await audit(event, 'cosmetic.update', id, input.name)
  return { id }
})
