export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  const row = await db(event)
    .prepare("SELECT sha256, width, height, created_at FROM capes WHERE uuid = ? AND status = 'active' LIMIT 1")
    .bind(player.uuid)
    .first<{ sha256: string; width: number; height: number; created_at: number }>()
  const removed = row
    ? null
    : await db(event)
        .prepare("SELECT removed_reason FROM capes WHERE uuid = ? AND status = 'removed' ORDER BY removed_at DESC LIMIT 1")
        .bind(player.uuid)
        .first<{ removed_reason: string | null }>()
  const flags = await db(event).prepare('SELECT upload_banned FROM players WHERE uuid = ?').bind(player.uuid).first<{ upload_banned: number }>()
  return {
    cape: row ? { url: capeUrl(event, row.sha256), sha256: row.sha256, width: row.width, height: row.height, uploadedAt: row.created_at } : null,
    lastRemovalReason: removed?.removed_reason ?? null,
    uploadsAllowed: !flags?.upload_banned
  }
})
