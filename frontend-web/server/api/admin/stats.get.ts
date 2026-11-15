export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const now = nowSeconds()
  const database = db(event)
  const [online, players, active, reported, news, cosmetics] = await database.batch([
    database.prepare('SELECT COUNT(*) AS count FROM players WHERE online = 1 AND last_seen >= ?').bind(now - PRESENCE_TTL_SECONDS),
    database.prepare('SELECT COUNT(*) AS count FROM players'),
    database.prepare("SELECT COUNT(*) AS count FROM capes WHERE status = 'active'"),
    database.prepare("SELECT COUNT(*) AS count FROM capes WHERE status = 'active' AND reports > 0"),
    database.prepare('SELECT COUNT(*) AS count FROM news WHERE published = 1'),
    database.prepare('SELECT COUNT(*) AS count FROM cosmetics WHERE enabled = 1')
  ])
  const count = (result: { results: unknown[] }) => (result.results[0] as { count: number } | undefined)?.count ?? 0
  return {
    online: count(online),
    players: count(players),
    activeCapes: count(active),
    reportedCapes: count(reported),
    publishedNews: count(news),
    cosmetics: count(cosmetics)
  }
})
