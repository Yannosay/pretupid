export default defineEventHandler(async (event) => {
  const catalog = await loadCatalog(event)
  const etag = `"catalog-${catalog.version}-${catalog.capes.length}-${catalog.trails.length}-${catalog.emotes.length}"`
  setResponseHeader(event, 'Cache-Control', 'public, max-age=60')
  setResponseHeader(event, 'ETag', etag)
  if (getRequestHeader(event, 'if-none-match') === etag) {
    setResponseStatus(event, 304)
    return null
  }
  return catalog
})
