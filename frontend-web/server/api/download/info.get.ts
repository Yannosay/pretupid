export default defineEventHandler(async (event) => {
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const [launcher, client] = await Promise.all([
    latestLauncherRelease(event).catch(() => null),
    latestClientRelease(event).catch(() => null)
  ])
  const available = (platform: LauncherPlatform, variant: LauncherVariant) => Boolean(launcher && launcherAsset(launcher, platform, variant))
  return {
    launcher: launcher
      ? {
          version: launcher.tag_name.replace(/^v/, ''),
          publishedAt: launcher.published_at,
          windows: available('windows', 'default'),
          macosArm64: available('macos', 'arm64'),
          macosX64: available('macos', 'x64'),
          linuxAppImage: available('linux', 'appimage'),
          linuxDeb: available('linux', 'deb'),
          linuxRpm: available('linux', 'rpm')
        }
      : null,
    client: client ? { version: client.version, minecraft: client.minecraft } : null
  }
})
