export default defineEventHandler(async (event) => {
  noStore(event)
  await rateLimit(event, `verify:${clientIp(event)}`, 30, 600)
  const body = await readJson<{ challengeId: unknown; profileKey: Partial<ProfileKeyProof>; textures: Partial<SignedTextures> }>(event, 16384)
  const challenge = await verifyToken(event, body.challengeId, 'challenge')
  if (!challenge || !challenge.serverId || !isUuid(challenge.sub)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid or expired challenge' })
  }
  const uuid = challenge.sub
  const ownsKey = await verifyProfileKey(uuid, challenge.serverId, body.profileKey)
  const name = ownsKey ? await verifiedProfileName(uuid, body.textures) : null
  if (!ownsKey || !name) {
    throw createError({ statusCode: 403, statusMessage: 'Account could not be verified' })
  }
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
