export type VisitorPlatform = 'windows' | 'macos' | 'linux'

function detect(): VisitorPlatform {
  if (typeof navigator === 'undefined') return 'windows'
  const source = `${navigator.userAgent} ${navigator.platform ?? ''}`.toLowerCase()
  if (source.includes('mac')) return 'macos'
  if (source.includes('linux') && !source.includes('android')) return 'linux'
  return 'windows'
}

export function platformLabel(platform: VisitorPlatform): string {
  return platform === 'macos' ? 'macOS' : platform === 'linux' ? 'Linux' : 'Windows'
}

export function useVisitorPlatform() {
  const platform = useState<VisitorPlatform>('visitor-platform', () => 'windows')
  onMounted(() => {
    platform.value = detect()
  })
  return { platform }
}
