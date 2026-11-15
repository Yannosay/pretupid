export default defineEventHandler(async (event) => {
  noStore(event)
  await rateLimit(event, `challenge:${clientIp(event)}`, 30, 600)
  const body = await readJson<{ uuid: unknown; name: unknown }>(event)
  if (!isUuid(body.uuid) || !isPlayerName(body.name)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid player' })
  }
  const serverId = randomHex(16)
  const challengeId = await signToken(event, {
    purpose: 'challenge',
    sub: body.uuid,
    name: body.name,
    serverId,
    exp: nowSeconds() + CHALLENGE_TTL_SECONDS
  })
  return { challengeId, serverId, expiresIn: CHALLENGE_TTL_SECONDS }
})
