import type { H3Event } from 'h3'

export async function rateLimit(event: H3Event, key: string, limit: number, windowSeconds: number): Promise<void> {
  const now = nowSeconds()
  const windowStart = now - (now % windowSeconds)
  const row = await db(event)
    .prepare(
      `INSERT INTO rate_limits (key, window_start, count) VALUES (?1, ?2, 1)
       ON CONFLICT(key) DO UPDATE SET
         count = CASE WHEN rate_limits.window_start = ?2 THEN rate_limits.count + 1 ELSE 1 END,
         window_start = ?2
       RETURNING count`
    )
    .bind(key, windowStart)
    .first<{ count: number }>()
  if (row && row.count > limit) {
    setResponseHeader(event, 'Retry-After', windowStart + windowSeconds - now)
    throw createError({ statusCode: 429, statusMessage: 'Too many requests, slow down' })
  }
}

export async function pruneRateLimits(event: H3Event): Promise<void> {
  await db(event).prepare('DELETE FROM rate_limits WHERE window_start < ?').bind(nowSeconds() - 86_400).run()
}
