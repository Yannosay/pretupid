export default defineEventHandler(async (event) => {
  const minecraft = String(getQuery(event).mc ?? '')
  if (!/^[0-9][0-9a-z.\-]{0,15}$/.test(minecraft)) {
    throw createError({ statusCode: 400, statusMessage: 'Unknown Minecraft version' })
  }
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const best = (await clientReleases(event)).find((entry) => entry.manifest.minecraft === minecraft)
  if (!best) {
    return { available: false }
  }
  return {
    available: true,
    version: best.manifest.version,
    minecraft: best.manifest.minecraft,
    url: best.asset.browser_download_url,
    sha256: best.manifest.sha256,
    sha1: best.manifest.sha1,
    sha512: best.manifest.sha512,
    size: best.manifest.size,
    notes: (best.release.body ?? '').slice(0, 4000),
    page: best.release.html_url,
    publishedAt: best.release.published_at
  }
})
