import type { H3Event } from 'h3'

export function serveImage(event: H3Event, object: R2ObjectBody, cacheControl: string) {
  setResponseHeaders(event, {
    'Content-Type': 'image/png',
    'Content-Length': String(object.size),
    'Cache-Control': cacheControl,
    ETag: object.httpEtag,
    'X-Content-Type-Options': 'nosniff',
    'Content-Security-Policy': "default-src 'none'; sandbox",
    'Cross-Origin-Resource-Policy': 'cross-origin',
    'Content-Disposition': 'inline'
  })
  if (getRequestHeader(event, 'if-none-match') === object.httpEtag) {
    setResponseStatus(event, 304)
    return null
  }
  return sendStream(event, object.body)
}
