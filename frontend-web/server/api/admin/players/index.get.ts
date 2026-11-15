export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const search = String(getQuery(event).q ?? '').trim().toLowerCase()
  const now = nowSeconds()
  const statement =
    search.length > 0
      ? db(event)
          .prepare(
            `SELECT uuid, name, first_seen, last_seen, online, client_version, upload_banned, banned FROM players
             WHERE name_lower LIKE ? ESCAPE '\\' OR uuid = ? ORDER BY last_seen DESC LIMIT 50`
          )
          .bind(`${search.replace(/[\\%_]/g, (char) => `\\${char}`)}%`, search)
      : db(event)
          .prepare('SELECT uuid, name, first_seen, last_seen, online, client_version, upload_banned, banned FROM players ORDER BY last_seen DESC LIMIT 50')
  const { results } = await statement.all<PlayerRow & { first_seen: number; client_version: string; banned: number }>()
  return {
    players: results.map((row) => ({
      uuid: row.uuid,
      name: row.name,
      firstSeen: row.first_seen,
      lastSeen: row.last_seen,
      online: isOnline(row, now),
      clientVersion: row.client_version,
      uploadBanned: row.upload_banned === 1,
      banned: row.banned === 1
    }))
  }
})
