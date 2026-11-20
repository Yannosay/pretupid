import type { H3Event } from 'h3'

export async function pruneScreenies(event: H3Event, before: number): Promise<void> {
  const { results } = await db(event).prepare('SELECT id FROM screenies WHERE created_at < ? LIMIT 200').bind(before).all<{ id: string }>()
  if (results.length === 0) {
    return
  }
  await contentBucket(event).delete(results.map((row) => `screenies/${row.id}.png`))
  await db(event)
    .prepare(`DELETE FROM screenies WHERE id IN (${results.map(() => '?').join(', ')})`)
    .bind(...results.map((row) => row.id))
    .run()
}
