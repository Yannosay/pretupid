export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `emote:${player.uuid}`, 20, 60)
  const body = await readJson<{ id: unknown }>(event)
  if (body.id === null) {
    await db(event).prepare('UPDATE players SET emote_id = NULL, emote_at = NULL WHERE uuid = ?').bind(player.uuid).run()
    return { ok: true }
  }
  if (!isCosmeticId(body.id)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid emote' })
  }
  const emote = await db(event).prepare("SELECT 1 AS ok FROM cosmetics WHERE id = ? AND type = 'emote' AND enabled = 1").bind(body.id).first()
  if (!emote) {
    throw createError({ statusCode: 404, statusMessage: 'Unknown emote' })
  }
  await db(event).prepare('UPDATE players SET emote_id = ?, emote_at = ? WHERE uuid = ?').bind(body.id, nowSeconds(), player.uuid).run()
  return { ok: true }
})
