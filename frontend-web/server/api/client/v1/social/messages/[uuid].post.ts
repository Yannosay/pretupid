const MAX_MESSAGE_LENGTH = 256

export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  const friend = routeParam(event, 'uuid')
  if (!isUuid(friend) || !(await areFriends(event, player.uuid, friend))) {
    throw createError({ statusCode: 404, statusMessage: 'Friend not found' })
  }
  await rateLimit(event, `message:${player.uuid}`, 30, 60)
  const body = await readJson<{ kind: unknown; text: unknown; address: unknown; label: unknown; lan: unknown; minecraft: unknown }>(event)
  let kind: 'text' | 'invite' = 'text'
  let stored: string
  if (body.kind === 'invite') {
    await rateLimit(event, `invite:${player.uuid}`, 10, 300)
    const address = cleanServerAddress(body.address)
    if (!address) {
      throw createError({ statusCode: 400, statusMessage: 'Invalid server address' })
    }
    kind = 'invite'
    const minecraft = typeof body.minecraft === 'string' && /^[0-9][0-9a-z.\-]{0,15}$/.test(body.minecraft) ? body.minecraft : undefined
    stored = JSON.stringify({ address, label: cleanSingleLine(body.label, 48), lan: body.lan === true, minecraft })
  } else {
    stored = cleanText(body.text, MAX_MESSAGE_LENGTH).replace(/\n{3,}/g, '\n\n')
    if (!stored) {
      throw createError({ statusCode: 400, statusMessage: 'Message is empty' })
    }
  }
  const now = nowSeconds()
  const result = await db(event)
    .prepare('INSERT INTO messages (from_uuid, to_uuid, kind, body, created_at) VALUES (?, ?, ?, ?, ?)')
    .bind(player.uuid, friend, kind, stored, now)
    .run()
  pushToPlayer(event, friend, { t: 'social' })
  return {
    message: {
      id: result.meta.last_row_id ?? 0,
      from: player.uuid,
      to: friend,
      kind,
      body: kind === 'invite' ? JSON.parse(stored) : stored,
      at: now,
      read: false
    }
  }
})
