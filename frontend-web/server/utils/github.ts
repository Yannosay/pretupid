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

const FRESH_SECONDS = 5 * 60
const RELEASES_KEY = 'github:releases'
let memory: { expires: number; releases: GithubRelease[] } | null = null

function githubHeaders(event: H3Event): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'pretupid-update-service',
    'X-GitHub-Api-Version': '2022-11-28'
  }
  const token = useRuntimeConfig(event).githubToken
  if (typeof token === 'string' && token.length > 0) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

function slim(releases: GithubRelease[]): GithubRelease[] {
  return releases
    .filter((release) => !release.draft)
    .map((release) => ({
      tag_name: release.tag_name,
      name: release.name,
      body: release.body ? release.body.slice(0, 4000) : null,
      draft: false,
      prerelease: release.prerelease,
      published_at: release.published_at,
      html_url: release.html_url,
      assets: release.assets.map((asset) => ({ name: asset.name, browser_download_url: asset.browser_download_url }))
    }))
}

async function stored<T>(event: H3Event, key: string): Promise<{ value: T; updatedAt: number } | null> {
  const row = await db(event).prepare('SELECT value, updated_at FROM app_cache WHERE key = ?').bind(key).first<{ value: string; updated_at: number }>()
  if (!row) {
    return null
  }
  try {
    return { value: JSON.parse(row.value) as T, updatedAt: row.updated_at }
  } catch {
    return null
  }
}

async function store(event: H3Event, key: string, value: unknown): Promise<void> {
  await db(event)
    .prepare('INSERT INTO app_cache (key, value, updated_at) VALUES (?1, ?2, ?3) ON CONFLICT(key) DO UPDATE SET value = ?2, updated_at = ?3')
    .bind(key, JSON.stringify(value), nowSeconds())
    .run()
}

export async function githubReleases(event: H3Event): Promise<GithubRelease[]> {
  if (memory && memory.expires > Date.now()) {
    return memory.releases
  }
  const cached = await stored<GithubRelease[]>(event, RELEASES_KEY)
  if (cached && cached.updatedAt > nowSeconds() - FRESH_SECONDS) {
    memory = { expires: (cached.updatedAt + FRESH_SECONDS) * 1000, releases: cached.value }
    return cached.value
  }
  const repository = useRuntimeConfig(event).public.repository
  try {
    const response = await fetch(`https://api.github.com/repos/${repository}/releases?per_page=50`, {
      headers: githubHeaders(event),
      signal: AbortSignal.timeout(8000)
    })
    if (!response.ok) {
      throw new Error(`GitHub responded ${response.status}`)
    }
    const releases = slim((await response.json()) as GithubRelease[])
    await store(event, RELEASES_KEY, releases)
    memory = { expires: Date.now() + FRESH_SECONDS * 1000, releases }
    return releases
  } catch (error) {
    if (cached) {
      memory = { expires: Date.now() + 60_000, releases: cached.value }
      return cached.value
    }
    console.error('GitHub releases unavailable', error instanceof Error ? error.message : error)
    throw createError({ statusCode: 502, statusMessage: 'Release service is unavailable' })
  }
}

export async function releaseAssetJson<T>(event: H3Event, release: GithubRelease, assetName: string): Promise<T | null> {
  const asset = release.assets.find((candidate) => candidate.name === assetName)
  if (!asset) {
    return null
  }
  const key = `github:asset:${asset.browser_download_url}`
  const cached = await stored<T>(event, key)
  if (cached) {
    return cached.value
  }
  const response = await fetch(asset.browser_download_url, {
    headers: { 'User-Agent': 'pretupid-update-service' },
    signal: AbortSignal.timeout(8000),
    redirect: 'follow'
  })
  if (!response.ok) {
    return null
  }
  const value = (await response.json()) as T
  await store(event, key, value)
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
