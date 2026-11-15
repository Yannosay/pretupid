export default defineEventHandler(async (event) => {
  noStore(event)
  await rateLimit(event, `lookup:${clientIp(event)}`, 120, 60)
  const body = await readJson<{ uuids: unknown }>(event)
  if (!Array.isArray(body.uuids) || body.uuids.length > MAX_LOOKUP) {
    throw createError({ statusCode: 400, statusMessage: `Provide up to ${MAX_LOOKUP} player ids` })
  }
  return { users: await publicProfiles(event, body.uuids as string[]) }
})
