export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const sha = routeParam(event, 'sha')
  if (!isSha256(sha)) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }
  const object = await contentBucket(event).get(`capes/${sha}.png`)
  if (!object) {
    throw createError({ statusCode: 404, statusMessage: 'Image was deleted' })
  }
  return serveImage(event, object, 'private, no-store')
})
