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
  const { results } = await db(event).prepare('SELECT * FROM cosmetics ORDER BY type, sort, name').all<CosmeticRow>()
  return {
    cosmetics: results.map((row) => ({
      id: row.id,
      type: row.type,
      name: row.name,
      description: row.description,
      imageUrl: row.asset_key ? `/api/content/${row.asset_key}` : null,
      data: parseJsonColumn(row.data, {}),
      enabled: row.enabled === 1,
      sort: row.sort,
      updatedAt: row.updated_at
    })),
    particles: TRAIL_PARTICLES,
    patterns: TRAIL_PATTERNS,
    parts: EMOTE_PARTS
  }
})
