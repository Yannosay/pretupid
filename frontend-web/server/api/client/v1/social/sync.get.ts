interface FriendRow {
  uuid: string
  name: string
  last_seen: number
  online: number
  server_address: string | null
  share_server: number
  minecraft_version: string | null
  created_at: number
}

interface MessageRow {
  id: number
  from_uuid: string
  to_uuid: string
  kind: 'text' | 'invite' | 'screenie'
  body: string
  created_at: number
  read_at: number | null
}

export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await rateLimit(event, `sync:${player.uuid}`, 40, 60)
  const query = getQuery(event)
  const initial = query.after === 'init'
  const after = initial ? 0 : Math.max(0, Number.parseInt(String(query.after ?? '0'), 10) || 0)
  const now = nowSeconds()
  const database = db(event)
  const [friends, incoming, outgoing, messages, unread, blocked] = await database.batch([
    database
      .prepare(
        `SELECT p.uuid, p.name, p.last_seen, p.online, p.server_address, p.share_server, p.minecraft_version, f.created_at
         FROM friendships f JOIN players p ON p.uuid = f.friend WHERE f.uuid = ? ORDER BY p.name_lower`
      )
      .bind(player.uuid),
    database
      .prepare('SELECT p.uuid, p.name, r.created_at FROM friend_requests r JOIN players p ON p.uuid = r.from_uuid WHERE r.to_uuid = ? ORDER BY r.created_at DESC LIMIT 50')
      .bind(player.uuid),
    database
      .prepare('SELECT p.uuid, p.name, r.created_at FROM friend_requests r JOIN players p ON p.uuid = r.to_uuid WHERE r.from_uuid = ? ORDER BY r.created_at DESC LIMIT 50')
      .bind(player.uuid),
    initial
      ? database.prepare('SELECT MAX(id) AS id FROM messages WHERE to_uuid = ?1 OR from_uuid = ?1').bind(player.uuid)
      : database
          .prepare('SELECT id, from_uuid, to_uuid, kind, body, created_at, read_at FROM messages WHERE id > ?2 AND (to_uuid = ?1 OR from_uuid = ?1) ORDER BY id LIMIT 100')
          .bind(player.uuid, after),
    database.prepare('SELECT from_uuid, COUNT(*) AS count FROM messages WHERE to_uuid = ? AND read_at IS NULL GROUP BY from_uuid').bind(player.uuid),
    database.prepare('SELECT b.blocked AS uuid, p.name FROM blocks b LEFT JOIN players p ON p.uuid = b.blocked WHERE b.uuid = ?').bind(player.uuid)
  ])
  const unreadBy = new Map((unread.results as { from_uuid: string; count: number }[]).map((row) => [row.from_uuid, row.count]))
  const messageRows = initial ? [] : (messages.results as MessageRow[])
  const latest = initial ? ((messages.results[0] as { id: number | null } | undefined)?.id ?? 0) : after
  return {
    now,
    cursor: messageRows.length > 0 ? messageRows[messageRows.length - 1].id : latest,
    friends: (friends.results as FriendRow[]).map((row) => {
      const online = isOnline(row, now)
      return {
        uuid: row.uuid,
        name: row.name,
        online,
        lastSeen: row.last_seen,
        server: online && row.share_server ? row.server_address : null,
        minecraft: online ? row.minecraft_version : null,
        since: row.created_at,
        unread: unreadBy.get(row.uuid) ?? 0
      }
    }),
    incoming: incoming.results,
    outgoing: outgoing.results,
    blocked: blocked.results,
    messages: messageRows.map((row) => ({
      id: row.id,
      from: row.from_uuid,
      to: row.to_uuid,
      kind: row.kind,
      body: row.kind === 'text' ? row.body : parseJsonColumn(row.body, null),
      at: row.created_at,
      read: row.read_at !== null
    }))
  }
})
