export default defineEventHandler(async (event) => {
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const releases = await githubReleases(event)
  const release = releases.find(
    (candidate) => /^v\d/.test(candidate.tag_name) && candidate.assets.some((asset) => asset.name === 'latest.json')
  )
  if (!release) {
    throw createError({ statusCode: 404, statusMessage: 'No launcher release available' })
  }
  const manifest = await releaseAssetJson<Record<string, unknown>>(release, 'latest.json')
  if (!manifest) {
    throw createError({ statusCode: 404, statusMessage: 'No launcher release available' })
  }
  return manifest
})
