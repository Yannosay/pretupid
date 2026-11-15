export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `report:${player.uuid}`, 20, 3600)
  const body = await readJson<{ uuid: unknown }>(event)
  if (!isUuid(body.uuid) || body.uuid === player.uuid) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid player' })
  }
  const cape = await db(event)
    .prepare("SELECT id FROM capes WHERE uuid = ? AND status = 'active' LIMIT 1")
    .bind(body.uuid)
    .first<{ id: string }>()
  if (!cape) {
    throw createError({ statusCode: 404, statusMessage: 'That player has no custom cape' })
  }
  const inserted = await db(event)
    .prepare('INSERT OR IGNORE INTO cape_reports (cape_id, reporter, created_at) VALUES (?, ?, ?)')
    .bind(cape.id, player.uuid, nowSeconds())
    .run()
  if (inserted.meta.changes) {
    await db(event).prepare('UPDATE capes SET reports = reports + 1 WHERE id = ?').bind(cape.id).run()
  }
  return { ok: true }
})
