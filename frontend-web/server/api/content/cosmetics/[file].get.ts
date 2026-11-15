export default defineEventHandler(async (event) => {
  const file = routeParam(event, 'file')
  if (!/^[a-z0-9_-]{1,32}-[0-9a-f]{16}\.png$/.test(file)) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }
  const object = await contentBucket(event).get(`cosmetics/${file}`)
  if (!object) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }
  return serveImage(event, object, 'public, max-age=86400, immutable')
})
