export default defineEventHandler(async (event) => {
  const requested = getQuery(event).mc
  const minecraft = typeof requested === 'string' && /^[0-9][0-9a-z.\-]{0,15}$/.test(requested) ? requested : null
  const release = minecraft
    ? ((await latestClientBuilds(event).catch(() => [])).find((entry) => entry.manifest.minecraft === minecraft) ?? null)
    : await latestClientRelease(event).catch(() => null)
  if (!release) {
    return sendRedirect(event, '/download?unavailable=client', 302)
  }
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  return sendRedirect(event, release.asset.browser_download_url, 302)
})
