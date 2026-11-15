const MAX_CAPE_BYTES = 128 * 1024

export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  const contentType = (getRequestHeader(event, 'content-type') ?? '').toLowerCase()
  if (contentType !== 'image/png') {
    throw createError({ statusCode: 415, statusMessage: 'Upload a PNG image' })
  }
  const flags = await db(event).prepare('SELECT upload_banned FROM players WHERE uuid = ?').bind(player.uuid).first<{ upload_banned: number }>()
  if (!flags) {
    throw createError({ statusCode: 401, statusMessage: 'Connect to Pretupid before uploading' })
  }
  if (flags.upload_banned) {
    throw createError({ statusCode: 403, statusMessage: 'Cape uploads are disabled for this account' })
  }
  await rateLimit(event, `cape-upload:${player.uuid}`, 6, 86_400)
  const raw = await readBytes(event, MAX_CAPE_BYTES)
  let sanitized: SanitizedPng
  try {
    sanitized = await sanitizePng(raw, isCapeSize)
  } catch (error) {
    throw createError({ statusCode: 400, statusMessage: error instanceof Error ? error.message : 'Invalid image' })
  }
  const sha256 = await sha256Hex(sanitized.bytes)
  const blocked = await db(event).prepare("SELECT 1 AS ok FROM capes WHERE sha256 = ? AND status = 'removed' LIMIT 1").bind(sha256).first()
  if (blocked) {
    throw createError({ statusCode: 403, statusMessage: 'This image was removed by moderation and cannot be uploaded' })
  }
  await contentBucket(event).put(`capes/${sha256}.png`, sanitized.bytes, {
    httpMetadata: { contentType: 'image/png', cacheControl: 'public, max-age=86400' }
  })
  const now = nowSeconds()
  const id = randomHex(12)
  await db(event).batch([
    db(event).prepare("UPDATE capes SET status = 'replaced' WHERE uuid = ? AND status = 'active'").bind(player.uuid),
    db(event)
      .prepare("INSERT INTO capes (id, uuid, sha256, width, height, size, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'active', ?)")
      .bind(id, player.uuid, sha256, sanitized.width, sanitized.height, sanitized.bytes.byteLength, now),
    db(event)
      .prepare("UPDATE players SET equipped = json_set(CASE WHEN json_valid(equipped) THEN equipped ELSE '{}' END, '$.cape', 'custom') WHERE uuid = ?")
      .bind(player.uuid)
  ])
  return { cape: { url: capeUrl(event, sha256), sha256, width: sanitized.width, height: sanitized.height, uploadedAt: now } }
})
