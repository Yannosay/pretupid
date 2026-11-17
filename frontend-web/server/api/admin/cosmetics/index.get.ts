interface CosmeticRow {
  id: string
  type: string
  name: string
  description: string
  asset_key: string | null
  asset_sha256: string | null
  data: string
  enabled: number
  sort: number
  updated_at: number
}

export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const database = db(event)
  const [rows, usage, customCapes] = await database.batch([
    database.prepare('SELECT * FROM cosmetics ORDER BY type, sort, name'),
    database.prepare(
      `SELECT slot, item, COUNT(*) AS players FROM (
         SELECT 'cape' AS slot, json_extract(equipped, '$.cape') AS item FROM players
         UNION ALL SELECT 'trail', json_extract(equipped, '$.trail') FROM players
         UNION ALL SELECT 'hat', json_extract(equipped, '$.hat') FROM players
         UNION ALL SELECT 'back', json_extract(equipped, '$.back') FROM players
         UNION ALL SELECT 'pet', json_extract(equipped, '$.pet') FROM players
       ) WHERE item IS NOT NULL AND item != '' GROUP BY slot, item`
    ),
    database.prepare("SELECT COUNT(*) AS count FROM capes WHERE status = 'active'")
  ])
  const equipped = new Map((usage.results as { slot: string; item: string; players: number }[]).map((row) => [`${row.slot}:${row.item}`, row.players]))
  const usageKey = (row: CosmeticRow) => (row.type === 'cape' ? `cape:catalog:${row.id}` : `${row.type}:${row.id}`)
  return {
    cosmetics: (rows.results as CosmeticRow[]).map((row) => ({
      id: row.id,
      type: row.type,
      name: row.name,
      description: row.description,
      imageUrl: row.asset_key ? `/api/content/${row.asset_key}` : null,
      data: parseJsonColumn(row.data, {}),
      enabled: row.enabled === 1,
      sort: row.sort,
      updatedAt: row.updated_at,
      equipped: row.type === 'emote' ? null : (equipped.get(usageKey(row)) ?? 0)
    })),
    customCapes: {
      active: (customCapes.results[0] as { count: number } | undefined)?.count ?? 0,
      equipped: equipped.get('cape:custom') ?? 0
    },
    particles: TRAIL_PARTICLES,
    patterns: TRAIL_PATTERNS,
    parts: EMOTE_PARTS,
    wearable: {
      patterns: BOX_PATTERNS,
      properties: CHANNEL_PROPERTIES,
      waves: CHANNEL_WAVES,
      limits: WEARABLE_LIMITS
    }
  }
})
