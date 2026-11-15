export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const raw = await readJson<Record<string, unknown>>(event, 256 * 1024)
  if (!isCosmeticId(raw.id)) {
    throw createError({ statusCode: 400, statusMessage: 'Id must be lowercase letters, digits, - or _ (max 32)' })
  }
  const id = raw.id
  const input = parseCosmeticInput(raw, true)
  const exists = await db(event).prepare('SELECT 1 AS ok FROM cosmetics WHERE id = ?').bind(id).first()
  if (exists) {
    throw createError({ statusCode: 409, statusMessage: 'A cosmetic with this id already exists' })
  }
  const asset = input.image ? await storeCosmeticImage(event, id, input.image) : null
  const now = nowSeconds()
  await db(event)
    .prepare(
      `INSERT INTO cosmetics (id, type, name, description, asset_key, asset_sha256, data, enabled, sort, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    )
    .bind(id, input.type, input.name, input.description, asset?.key ?? null, asset?.sha256 ?? null, input.data, input.enabled ? 1 : 0, input.sort, now, now)
    .run()
  await audit(event, 'cosmetic.create', id, `${input.type} ${input.name}`)
  return { id }
})
