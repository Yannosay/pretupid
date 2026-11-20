const MAX_SCREENIE_BYTES = 12 * 1024 * 1024
const MAX_SCREENIE_SIDE = 7680

export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  const friend = routeParam(event, 'uuid')
  if (!isUuid(friend) || !(await areFriends(event, player.uuid, friend))) {
    throw createError({ statusCode: 404, statusMessage: 'Friend not found' })
  }
  const contentType = (getRequestHeader(event, 'content-type') ?? '').toLowerCase()
  if (contentType !== 'image/png') {
    throw createError({ statusCode: 415, statusMessage: 'Send a PNG image' })
  }
  await rateLimit(event, `screenie:${player.uuid}`, 12, 3600)
  const raw = await readBytes(event, MAX_SCREENIE_BYTES)
  let sanitized: SanitizedPng
  try {
    sanitized = await sanitizePng(raw, (width, height) => width > 0 && height > 0 && width <= MAX_SCREENIE_SIDE && height <= MAX_SCREENIE_SIDE)
  } catch (error) {
    throw createError({ statusCode: 400, statusMessage: error instanceof Error ? error.message : 'Invalid image' })
  }
  const id = randomHex(16)
  await contentBucket(event).put(`screenies/${id}.png`, sanitized.bytes, {
    httpMetadata: { contentType: 'image/png', cacheControl: 'private, max-age=86400' }
  })
  const now = nowSeconds()
  const body = JSON.stringify({ id, width: sanitized.width, height: sanitized.height })
  const [, inserted] = await db(event).batch([
    db(event)
      .prepare('INSERT INTO screenies (id, owner, recipient, width, height, size, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
      .bind(id, player.uuid, friend, sanitized.width, sanitized.height, sanitized.bytes.byteLength, now),
    db(event).prepare("INSERT INTO messages (from_uuid, to_uuid, kind, body, created_at) VALUES (?, ?, 'screenie', ?, ?)").bind(player.uuid, friend, body, now)
  ])
  pushToPlayer(event, friend, { t: 'social' })
  return {
    message: {
      id: inserted.meta.last_row_id ?? 0,
      from: player.uuid,
      to: friend,
      kind: 'screenie',
      body: JSON.parse(body),
      at: now,
      read: false
    }
  }
})
