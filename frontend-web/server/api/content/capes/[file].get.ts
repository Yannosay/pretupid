export default defineEventHandler(async (event) => {
  const file = routeParam(event, 'file')
  const match = /^([0-9a-f]{64})\.png$/.exec(file)
  if (!match) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }
  const sha256 = match[1]
  const active = await db(event).prepare("SELECT 1 AS ok FROM capes WHERE sha256 = ? AND status = 'active' LIMIT 1").bind(sha256).first()
  if (!active) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }
  const object = await contentBucket(event).get(`capes/${sha256}.png`)
  if (!object) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }
  return serveImage(event, object, 'public, max-age=3600')
})
