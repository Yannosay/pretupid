export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const id = routeParam(event, 'id')
  if (!/^[0-9a-f]{24}$/.test(id)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid cape' })
  }
  const body = await readJson<{ action: unknown; reason: unknown; banUploads: unknown }>(event)
  const cape = await db(event).prepare('SELECT id, uuid, sha256, status FROM capes WHERE id = ?').bind(id).first<{
    id: string
    uuid: string
    sha256: string
    status: string
  }>()
  if (!cape) {
    throw createError({ statusCode: 404, statusMessage: 'Cape not found' })
  }
  const now = nowSeconds()
  if (body.action === 'remove') {
    const reason = cleanSingleLine(body.reason, 160) || 'Removed for violating the Pretupid rules'
    const statements = [
      db(event).prepare("UPDATE capes SET status = 'removed', removed_at = ?, removed_reason = ? WHERE id = ?").bind(now, reason, id),
      db(event).prepare('DELETE FROM cape_reports WHERE cape_id = ?').bind(id)
    ]
    if (body.banUploads === true) {
      statements.push(db(event).prepare('UPDATE players SET upload_banned = 1 WHERE uuid = ?').bind(cape.uuid))
    }
    await db(event).batch(statements)
    const stillUsed = await db(event)
      .prepare("SELECT 1 AS ok FROM capes WHERE sha256 = ? AND status = 'active' LIMIT 1")
      .bind(cape.sha256)
      .first()
    if (!stillUsed) {
      await contentBucket(event).delete(`capes/${cape.sha256}.png`)
    }
    await audit(event, 'cape.remove', cape.uuid, `${id} ${reason}${body.banUploads === true ? ' (uploads banned)' : ''}`)
    return { ok: true }
  }
  if (body.action === 'dismiss') {
    await db(event).batch([
      db(event).prepare('UPDATE capes SET reports = 0 WHERE id = ?').bind(id),
      db(event).prepare('DELETE FROM cape_reports WHERE cape_id = ?').bind(id)
    ])
    await audit(event, 'cape.dismiss_reports', cape.uuid, id)
    return { ok: true }
  }
  throw createError({ statusCode: 400, statusMessage: 'Unknown action' })
})
