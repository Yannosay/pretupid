const SESSION_SERVER = 'https://sessionserver.mojang.com/session/minecraft/hasJoined'

export default defineEventHandler(async (event) => {
  noStore(event)
  await rateLimit(event, `verify:${clientIp(event)}`, 30, 600)
  const body = await readJson<{ challengeId: unknown; name: unknown }>(event)
  const challenge = await verifyToken(event, body.challengeId, 'challenge')
  if (!challenge || !challenge.serverId || !isUuid(challenge.sub) || !isPlayerName(challenge.name) || body.name !== challenge.name) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid or expired challenge' })
  }
  const url = `${SESSION_SERVER}?username=${encodeURIComponent(challenge.name)}&serverId=${encodeURIComponent(challenge.serverId)}`
  const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(8000) })
  if (response.status !== 200) {
    throw createError({ statusCode: 403, statusMessage: 'Account could not be verified' })
  }
  const profile = (await response.json()) as { id?: unknown; name?: unknown }
  const uuid = typeof profile.id === 'string' ? dashedUuid(profile.id) : null
  if (uuid !== challenge.sub) {
    throw createError({ statusCode: 403, statusMessage: 'Account could not be verified' })
  }
  const name = isPlayerName(profile.name) ? profile.name : challenge.name
  const now = nowSeconds()
  const existing = await db(event).prepare('SELECT banned FROM players WHERE uuid = ?').bind(uuid).first<{ banned: number }>()
  if (existing?.banned) {
    throw createError({ statusCode: 403, statusMessage: 'This account is suspended from Pretupid services' })
  }
  await db(event)
    .prepare(
      `INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?1, ?2, ?3, ?4, ?4)
       ON CONFLICT(uuid) DO UPDATE SET name = ?2, name_lower = ?3`
    )
    .bind(uuid, name, name.toLowerCase(), now)
    .run()
  const token = await signToken(event, { purpose: 'session', sub: uuid, name, exp: now + SESSION_TTL_SECONDS })
  return { token, expiresIn: SESSION_TTL_SECONDS, uuid, name }
})
