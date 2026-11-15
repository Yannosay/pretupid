export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = routeParam(event, 'id')
  const existing = await db(event).prepare('SELECT type, asset_key, asset_sha256 FROM cosmetics WHERE id = ?').bind(id).first<{
    type: string
    asset_key: string | null
    asset_sha256: string | null
  }>()
  if (!existing) {
    throw createError({ statusCode: 404, statusMessage: 'Cosmetic not found' })
  }
  const raw = await readJson<Record<string, unknown>>(event, 256 * 1024)
  const input = parseCosmeticInput({ ...raw, type: existing.type }, !existing.asset_key)
  let assetKey = existing.asset_key
  let assetSha = existing.asset_sha256
  if (input.image) {
    const asset = await storeCosmeticImage(event, id, input.image)
    if (existing.asset_key && existing.asset_key !== asset.key) {
      await contentBucket(event).delete(existing.asset_key)
    }
    assetKey = asset.key
    assetSha = asset.sha256
  }
  await db(event)
    .prepare('UPDATE cosmetics SET name = ?, description = ?, asset_key = ?, asset_sha256 = ?, data = ?, enabled = ?, sort = ?, updated_at = ? WHERE id = ?')
    .bind(input.name, input.description, assetKey, assetSha, input.data, input.enabled ? 1 : 0, input.sort, nowSeconds(), id)
    .run()
  await audit(event, 'cosmetic.update', id, input.name)
  return { id }
})
