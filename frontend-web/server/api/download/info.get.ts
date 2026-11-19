export default defineEventHandler(async (event) => {
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const [launchers, client, builds] = await Promise.all([
    launcherReleases(event).catch(() => []),
    latestClientRelease(event).catch(() => null),
    latestClientBuilds(event).catch(() => [])
  ])
  const available = (platform: LauncherPlatform, variant: LauncherVariant) => newestLauncherAsset(launchers, platform, variant) !== null
  const launcher = launchers[0] ?? null
  return {
    launcher: launcher
      ? {
          version: launcher.version,
          publishedAt: launcher.release.published_at,
          windows: available('windows', 'default'),
          macosArm64: available('macos', 'arm64'),
          macosX64: available('macos', 'x64'),
          linuxAppImage: available('linux', 'appimage'),
          linuxDeb: available('linux', 'deb'),
          linuxRpm: available('linux', 'rpm')
        }
      : null,
    client: client
      ? {
          version: client.manifest.version,
          minecraft: client.manifest.minecraft,
          minecraftVersions: builds.map((entry) => entry.manifest.minecraft),
          publishedAt: client.release.published_at
        }
      : null
  }
})
