import { ref, onMounted } from 'vue'

const PROTOCOL = 'pretupid'

interface SyncPayload {
  theme: 'dark' | 'light'
  accent: string
  edge: number
}

export function buildDeepLink(action: string, params?: Record<string, string>): string {
  const base = `${PROTOCOL}://${action}`
  if (!params) return base
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value.length > 0) {
      search.set(key, value)
    }
  }
  const query = search.toString()
  return query.length > 0 ? `${base}?${query}` : base
}

export function useLauncherBridge() {
  const launched = ref(false)
  const lastSyncAt = ref<number | null>(null)

  function trigger(url: string) {
    if (typeof window === 'undefined') return

    const anchor = document.createElement('a')
    anchor.href = url
    anchor.rel = 'noopener noreferrer'
    anchor.style.display = 'none'
    document.body.appendChild(anchor)
    anchor.click()
    setTimeout(() => anchor.remove(), 0)

    launched.value = true
  }

  function openLauncher() {
    trigger(buildDeepLink('open'))
  }

  function syncSettings(payload: SyncPayload) {
    const params: Record<string, string> = {
      theme: payload.theme,
      accent: payload.accent,
      edge: String(payload.edge)
    }
    trigger(buildDeepLink('settings', params))
    lastSyncAt.value = Date.now()
  }

  onMounted(() => {
    launched.value = false
  })

  return {
    launched,
    lastSyncAt,
    openLauncher,
    syncSettings,
    buildDeepLink
  }
}