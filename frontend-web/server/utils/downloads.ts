import type { H3Event } from 'h3'
import type { GithubRelease } from './github'

export type LauncherPlatform = 'windows' | 'macos' | 'linux'
export type LauncherVariant = 'default' | 'arm64' | 'x64' | 'appimage' | 'deb' | 'rpm'

interface ClientManifest {
  version: string
  minecraft: string
  file: string
}

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

export async function latestLauncherRelease(event: H3Event): Promise<GithubRelease | null> {
  const releases = await githubReleases(event)
  return releases.find((release) => !release.prerelease && /^v\d/.test(release.tag_name) && release.assets.length > 0) ?? null
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

export async function latestClientRelease(event: H3Event) {
  const releases = await githubReleases(event)
  let best: { version: string; minecraft: string; url: string } | null = null
  for (const release of releases) {
    if (release.prerelease || !/^(client-)?v\d/.test(release.tag_name)) {
      continue
    }
    const manifest = await releaseAssetJson<ClientManifest>(event, release, 'client-manifest.json').catch(() => null)
    if (!manifest || typeof manifest.version !== 'string' || typeof manifest.file !== 'string') {
      continue
    }
    const asset = release.assets.find((candidate) => candidate.name === manifest.file)
    if (asset && (!best || compareVersions(manifest.version, best.version) > 0)) {
      best = { version: manifest.version, minecraft: String(manifest.minecraft ?? ''), url: asset.browser_download_url }
    }
  }
  return best
}
