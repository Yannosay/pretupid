export default defineEventHandler(async (event) => {
  noStore(event)
  requireSameOrigin(event)
  await rateLimit(event, `admin-login:${clientIp(event)}`, 5, 900)
  const configured = useRuntimeConfig(event).adminKey
  if (typeof configured !== 'string' || configured.length < 32) {
    throw createError({ statusCode: 503, statusMessage: 'Admin access is not configured' })
  }
  const body = await readJson<{ key: unknown }>(event, 2048)
  if (typeof body.key !== 'string' || !(await constantTimeEqual(body.key, configured))) {
    await audit(event, 'admin.login_failed', 'admin')
    throw createError({ statusCode: 401, statusMessage: 'Invalid admin key' })
  }
  await issueAdminSession(event)
  await audit(event, 'admin.login', 'admin')
  return { authenticated: true }
})
