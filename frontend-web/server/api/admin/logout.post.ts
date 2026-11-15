export default defineEventHandler(async (event) => {
  noStore(event)
  requireSameOrigin(event)
  clearAdminSession(event)
  return { authenticated: false }
})
