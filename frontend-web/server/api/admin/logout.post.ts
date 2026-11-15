export default defineEventHandler(async (event) => {
  noStore(event)
  requireSameOrigin(event)
  if (await hasAdminSession(event)) {
    await revokeAdminSessions(event)
    await audit(event, 'admin.logout', 'admin', 'All admin sessions revoked')
  }
  clearAdminSession(event)
  return { authenticated: false }
})
