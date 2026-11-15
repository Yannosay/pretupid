export default defineEventHandler(async (event) => {
  const platform = getRouterParam(event, 'platform')
  if (!isLauncherPlatform(platform)) {
    throw createError({ statusCode: 404, statusMessage: 'Unknown platform' })
  }
  const variant = variantFor(platform, getQuery(event).variant)
  const release = await latestLauncherRelease(event)
  const asset = release ? launcherAsset(release, platform, variant) : undefined
  if (!asset) {
    return sendRedirect(event, `/download?unavailable=${platform}`, 302)
  }
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  return sendRedirect(event, asset.browser_download_url, 302)
})
