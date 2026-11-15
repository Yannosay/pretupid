interface ClientManifest {
  version: string
  minecraft: string
  file: string
  sha256: string
  sha1: string
  sha512: string
  size: number
  url?: string
}

export default defineEventHandler(async (event) => {
  const minecraft = String(getQuery(event).mc ?? '')
  if (!/^[0-9][0-9a-z.\-]{0,15}$/.test(minecraft)) {
    throw createError({ statusCode: 400, statusMessage: 'Unknown Minecraft version' })
  }
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const releases = await githubReleases(event)
  let best: { manifest: ClientManifest; release: GithubRelease } | null = null
  for (const release of releases) {
    if (!release.tag_name.startsWith('client-v')) {
      continue
    }
    const manifest = await releaseAssetJson<ClientManifest>(release, 'client-manifest.json').catch(() => null)
    if (
      !manifest ||
      manifest.minecraft !== minecraft ||
      !isSha256(manifest.sha256) ||
      !/^[0-9a-f]{40}$/.test(manifest.sha1) ||
      !/^[0-9a-f]{128}$/.test(manifest.sha512) ||
      typeof manifest.version !== 'string'
    ) {
      continue
    }
    const asset = release.assets.find((candidate) => candidate.name === manifest.file)
    if (!asset) {
      continue
    }
    if (!best || compareVersions(manifest.version, best.manifest.version) > 0) {
      best = { manifest: { ...manifest, url: asset.browser_download_url }, release }
    }
  }
  if (!best) {
    return { available: false }
  }
  return {
    available: true,
    version: best.manifest.version,
    minecraft: best.manifest.minecraft,
    url: best.manifest.url,
    sha256: best.manifest.sha256,
    sha1: best.manifest.sha1,
    sha512: best.manifest.sha512,
    size: best.manifest.size,
    notes: (best.release.body ?? '').slice(0, 4000),
    page: best.release.html_url,
    publishedAt: best.release.published_at
  }
})
