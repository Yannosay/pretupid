import type { H3Event } from 'h3'

interface GithubAsset {
  name: string
  browser_download_url: string
}

export interface GithubRelease {
  tag_name: string
  name: string | null
  body: string | null
  draft: boolean
  prerelease: boolean
  published_at: string | null
  html_url: string
  assets: GithubAsset[]
}

const CACHE_MS = 5 * 60 * 1000
let cache: { expires: number; releases: GithubRelease[] } | null = null
const manifestCache = new Map<string, { expires: number; value: unknown }>()

export async function githubReleases(event: H3Event): Promise<GithubRelease[]> {
  if (cache && cache.expires > Date.now()) {
    return cache.releases
  }
  const repository = useRuntimeConfig(event).public.repository
  const response = await fetch(`https://api.github.com/repos/${repository}/releases?per_page=50`, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'pretupid-update-service' },
    signal: AbortSignal.timeout(8000)
  })
  if (!response.ok) {
    throw createError({ statusCode: 502, statusMessage: 'Release service is unavailable' })
  }
  const releases = (await response.json()) as GithubRelease[]
  cache = { expires: Date.now() + CACHE_MS, releases: releases.filter((release) => !release.draft) }
  return cache.releases
}

export async function releaseAssetJson<T>(release: GithubRelease, assetName: string): Promise<T | null> {
  const asset = release.assets.find((candidate) => candidate.name === assetName)
  if (!asset) {
    return null
  }
  const cached = manifestCache.get(asset.browser_download_url)
  if (cached && cached.expires > Date.now()) {
    return cached.value as T
  }
  const response = await fetch(asset.browser_download_url, { signal: AbortSignal.timeout(8000), redirect: 'follow' })
  if (!response.ok) {
    return null
  }
  const value = (await response.json()) as T
  manifestCache.set(asset.browser_download_url, { expires: Date.now() + CACHE_MS, value })
  return value
}

export function compareVersions(left: string, right: string): number {
  const parse = (value: string) => {
    const [core, stage = ''] = value.replace(/^v/, '').split('+')[0].split('-', 2)
    const numbers = core.split('.').map((part) => Number.parseInt(part, 10) || 0)
    return { numbers, stage }
  }
  const a = parse(left)
  const b = parse(right)
  for (let i = 0; i < Math.max(a.numbers.length, b.numbers.length); i++) {
    const difference = (a.numbers[i] ?? 0) - (b.numbers[i] ?? 0)
    if (difference !== 0) {
      return Math.sign(difference)
    }
  }
  if (a.stage === b.stage) {
    return 0
  }
  if (!a.stage) {
    return 1
  }
  if (!b.stage) {
    return -1
  }
  return a.stage.localeCompare(b.stage, 'en', { numeric: true })
}
