export default defineEventHandler(async (event) => {
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const releases = await launcherReleases(event)
  for (const entry of releases) {
    if (!entry.release.assets.some((asset) => asset.name === 'latest.json')) {
      continue
    }
    const manifest = await releaseAssetJson<Record<string, unknown>>(event, entry.release, 'latest.json').catch(() => null)
    if (manifest) {
      return manifest
    }
  }
  throw createError({ statusCode: 404, statusMessage: 'No launcher release available' })
})
