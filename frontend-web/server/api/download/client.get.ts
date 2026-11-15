export default defineEventHandler(async (event) => {
  const release = await latestClientRelease(event)
  if (!release) {
    return sendRedirect(event, '/download?unavailable=client', 302)
  }
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  return sendRedirect(event, release.url, 302)
})
