export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const uuid = routeParam(event, 'uuid')
  if (!isUuid(uuid)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid player' })
  }
  const body = await readJson<{ uploadBanned: unknown; banned: unknown }>(event)
  const player = await db(event).prepare('SELECT uuid, name FROM players WHERE uuid = ?').bind(uuid).first<{ uuid: string; name: string }>()
  if (!player) {
    throw createError({ statusCode: 404, statusMessage: 'Player not found' })
  }
  if (typeof body.uploadBanned === 'boolean') {
    await db(event).prepare('UPDATE players SET upload_banned = ? WHERE uuid = ?').bind(body.uploadBanned ? 1 : 0, uuid).run()
    await audit(event, body.uploadBanned ? 'player.upload_ban' : 'player.upload_unban', uuid, player.name)
  }
  if (typeof body.banned === 'boolean') {
    const statements = [db(event).prepare('UPDATE players SET banned = ?, online = 0 WHERE uuid = ?').bind(body.banned ? 1 : 0, uuid)]
    if (body.banned) {
      statements.push(
        db(event).prepare("UPDATE capes SET status = 'removed', removed_at = ?, removed_reason = 'Account suspended' WHERE uuid = ? AND status = 'active'").bind(nowSeconds(), uuid)
      )
    }
    await db(event).batch(statements)
    await audit(event, body.banned ? 'player.ban' : 'player.unban', uuid, player.name)
  }
  return { ok: true }
})
