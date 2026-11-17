import type { H3Event } from 'h3'
import type { GithubAsset, GithubRelease } from './github'

export type LauncherPlatform = 'windows' | 'macos' | 'linux'
export type LauncherVariant = 'default' | 'arm64' | 'x64' | 'appimage' | 'deb' | 'rpm'

export interface ClientManifest {
  version: string
  minecraft: string
  file: string
  sha256: string
  sha1: string
  sha512: string
  size: number
}

export interface ClientRelease {
  manifest: ClientManifest
  release: GithubRelease
  asset: GithubAsset
}

export interface LauncherRelease {
  version: string
  release: GithubRelease
}

const RELEASE_TAG = /^(?:client-|launcher-)?v(\d[0-9A-Za-z.+-]*)$/
const INSTALLER_EXTENSIONS = ['.exe', '.dmg', '.appimage', '.deb', '.rpm']

const VARIANTS: Record<LauncherPlatform, LauncherVariant[]> = {
  windows: ['default'],
  macos: ['arm64', 'x64'],
  linux: ['appimage', 'deb', 'rpm']
}

export function isLauncherPlatform(value: unknown): value is LauncherPlatform {
  return value === 'windows' || value === 'macos' || value === 'linux'
}

export function variantFor(platform: LauncherPlatform, requested: unknown): LauncherVariant {
  const allowed = VARIANTS[platform]
  return typeof requested === 'string' && (allowed as string[]).includes(requested) ? (requested as LauncherVariant) : allowed[0]
}

function isPublished(release: GithubRelease): boolean {
  return !release.draft && !release.prerelease && RELEASE_TAG.test(release.tag_name)
}

function tagVersion(release: GithubRelease): string {
  return RELEASE_TAG.exec(release.tag_name)?.[1] ?? '0'
}

function newestFirst<T extends { version: string; release: GithubRelease }>(entries: T[]): T[] {
  return entries.sort((a, b) => {
    const byVersion = compareVersions(b.version, a.version)
    if (byVersion !== 0) {
      return byVersion
    }
    return Date.parse(b.release.published_at ?? '') - Date.parse(a.release.published_at ?? '') || 0
  })
}

function hasInstaller(release: GithubRelease): boolean {
  return release.assets.some((asset) => {
    const name = asset.name.toLowerCase()
    return INSTALLER_EXTENSIONS.some((extension) => name.endsWith(extension))
  })
}

function isValidClientManifest(value: unknown): value is ClientManifest {
  if (value === null || typeof value !== 'object') {
    return false
  }
  const manifest = value as Record<string, unknown>
  return (
    typeof manifest.version === 'string' &&
    /^\d[0-9A-Za-z.+-]{0,40}$/.test(manifest.version) &&
    typeof manifest.minecraft === 'string' &&
    typeof manifest.file === 'string' &&
    manifest.file.endsWith('.jar') &&
    isSha256(manifest.sha256) &&
    typeof manifest.sha1 === 'string' &&
    /^[0-9a-f]{40}$/.test(manifest.sha1) &&
    typeof manifest.sha512 === 'string' &&
    /^[0-9a-f]{128}$/.test(manifest.sha512) &&
    typeof manifest.size === 'number' &&
    manifest.size > 0
  )
}

export async function launcherReleases(event: H3Event): Promise<LauncherRelease[]> {
  const candidates = (await githubReleases(event)).filter((release) => isPublished(release) && hasInstaller(release))
  const entries = await Promise.all(
    candidates.map(async (release) => {
      const updater = await releaseAssetJson<{ version?: unknown }>(event, release, 'latest.json').catch(() => null)
      const version = typeof updater?.version === 'string' && /^v?\d/.test(updater.version) ? updater.version.replace(/^v/, '') : tagVersion(release)
      return { version, release }
    })
  )
  return newestFirst(entries)
}

export async function latestLauncherRelease(event: H3Event): Promise<LauncherRelease | null> {
  return (await launcherReleases(event))[0] ?? null
}

export function launcherAsset(release: GithubRelease, platform: LauncherPlatform, variant: LauncherVariant) {
  const files = release.assets.filter((asset) => !asset.name.endsWith('.sig') && !asset.name.endsWith('.json'))
  const matches = (test: (name: string) => boolean) => files.find((asset) => test(asset.name.toLowerCase()))
  switch (platform) {
    case 'windows':
      return matches((name) => name.endsWith('-setup.exe')) ?? matches((name) => name.endsWith('.exe'))
    case 'macos': {
      const arch = variant === 'x64' ? ['x64', 'x86_64'] : ['aarch64', 'arm64']
      return matches((name) => name.endsWith('.dmg') && arch.some((tag) => name.includes(tag))) ?? matches((name) => name.endsWith('.dmg'))
    }
    case 'linux': {
      const extension = variant === 'deb' ? '.deb' : variant === 'rpm' ? '.rpm' : '.appimage'
      return matches((name) => name.endsWith(extension))
    }
  }
}

export function newestLauncherAsset(releases: LauncherRelease[], platform: LauncherPlatform, variant: LauncherVariant) {
  for (const entry of releases) {
    const asset = launcherAsset(entry.release, platform, variant)
    if (asset) {
      return { version: entry.version, asset }
    }
  }
  return null
}

export async function clientReleases(event: H3Event): Promise<ClientRelease[]> {
  const candidates = (await githubReleases(event)).filter(
    (release) => isPublished(release) && release.assets.some((asset) => asset.name === 'client-manifest.json')
  )
  const entries = await Promise.all(
    candidates.map(async (release) => {
      const manifest = await releaseAssetJson<unknown>(event, release, 'client-manifest.json').catch(() => null)
      if (!isValidClientManifest(manifest)) {
        return null
      }
      const asset = release.assets.find((candidate) => candidate.name === manifest.file)
      return asset ? { version: manifest.version, manifest, release, asset } : null
    })
  )
  return newestFirst(entries.filter((entry): entry is ClientRelease & { version: string } => entry !== null))
}

export async function latestClientRelease(event: H3Event): Promise<ClientRelease | null> {
  return (await clientReleases(event))[0] ?? null
}
