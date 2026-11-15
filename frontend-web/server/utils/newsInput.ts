export interface NewsInput {
  title: string
  summary: string
  body: string
  category: (typeof NEWS_CATEGORIES)[number]
  pinned: boolean
  published: boolean
}

export function parseNewsInput(raw: Record<string, unknown>): NewsInput {
  const title = cleanSingleLine(raw.title, 120)
  const body = cleanText(raw.body, 20_000)
  if (!title || !body) {
    throw createError({ statusCode: 400, statusMessage: 'Title and body are required' })
  }
  const category = NEWS_CATEGORIES.find((value) => value === raw.category)
  if (!category) {
    throw createError({ statusCode: 400, statusMessage: 'Unknown category' })
  }
  return {
    title,
    summary: cleanSingleLine(raw.summary, 240),
    body,
    category,
    pinned: raw.pinned === true,
    published: raw.published === true
  }
}
