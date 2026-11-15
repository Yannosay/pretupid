export default defineEventHandler(async (event) => {
  noStore(event)
  return { authenticated: await hasAdminSession(event) }
})
