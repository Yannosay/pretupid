import type { H3Event } from 'h3'

export const NEWS_CATEGORIES = ['news', 'update', 'announcement', 'event', 'maintenance'] as const

export interface NewsRow {
  id: string
  title: string
  summary: string
  body: string
  category: (typeof NEWS_CATEGORIES)[number]
  pinned: number
  published: number
  published_at: number | null
  created_at: number
  updated_at: number
}

export function presentNews(row: NewsRow, includeBody: boolean) {
  return {
    id: row.id,
    title: row.title,
    summary: row.summary,
    category: row.category,
    pinned: row.pinned === 1,
    publishedAt: row.published_at,
    updatedAt: row.updated_at,
    ...(includeBody ? { body: row.body } : {})
  }
}

export async function publishedNews(event: H3Event, limit: number, includeBody: boolean) {
  const { results } = await db(event)
    .prepare('SELECT * FROM news WHERE published = 1 ORDER BY pinned DESC, published_at DESC LIMIT ?')
    .bind(limit)
    .all<NewsRow>()
  return results.map((row) => presentNews(row, includeBody))
}

export function newsSlug(title: string): string {
  const base = title
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48)
  return `${base || 'post'}-${randomHex(3)}`
}
