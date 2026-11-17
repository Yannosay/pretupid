import { defineStore } from 'pinia'
import { ref } from 'vue'
import { check } from '@tauri-apps/plugin-updater'
import { REPOSITORY } from '@/config'
import { useSettingsStore } from '@/stores/settings'
import { useSplashStore } from '@/stores/splash'
import { useNotificationsStore } from '@/stores/notifications'

const PENDING_NOTES_KEY = 'pretupid-pending-release-notes'
const DEFERRED_UPDATE_KEY = 'pretupid-deferred-update-version'

export interface PendingReleaseNotes {
  version: string
  body: string
  publishedAt: string | null
  htmlUrl: string
}

type DownloadEvent =
  | { event: 'Started'; data?: { contentLength?: number } }
  | { event: 'Progress'; data?: { chunkLength?: number } }
  | { event: 'Finished'; data?: unknown }

interface TauriUpdate {
  version: string
  body?: string
  date?: string
  downloadAndInstall: (callback: (event: DownloadEvent) => void) => Promise<void>
}


async function fetchReleaseNotes(version: string): Promise<PendingReleaseNotes | null> {
  const repo = REPOSITORY
  const cleanVersion = version.replace(/^v/, '')
  const candidates = [`launcher-v${cleanVersion}`, `v${cleanVersion}`, cleanVersion]

  for (const tag of candidates) {
    try {
      const response = await fetch(
        `https://api.github.com/repos/${repo}/releases/tags/${tag}`,
        { headers: { Accept: 'application/vnd.github+json' } }
      )
      if (!response.ok) continue
      const data = await response.json()
      if (typeof data.name === 'string' && /^Pretupid Client /i.test(data.name)) continue
      return {
        version: typeof data.tag_name === 'string' ? data.tag_name : tag,
        body: typeof data.body === 'string' ? data.body : '',
        publishedAt: typeof data.published_at === 'string' ? data.published_at : null,
        htmlUrl:
          typeof data.html_url === 'string'
            ? data.html_url
            : `https://github.com/${repo}/releases/tag/${tag}`
      }
    } catch {
      continue
    }
  }

  return {
    version: `v${cleanVersion}`,
    body: '',
    publishedAt: null,
    htmlUrl: `https://github.com/${repo}/releases/tag/v${cleanVersion}`
  }
}

function savePendingNotes(notes: PendingReleaseNotes) {
  try {
    localStorage.setItem(PENDING_NOTES_KEY, JSON.stringify(notes))
  } catch {
    void 0
  }
}

function clearPendingNotes() {
  try {
    localStorage.removeItem(PENDING_NOTES_KEY)
  } catch {
    void 0
  }
}

function getDeferredVersion(): string | null {
  try {
    return localStorage.getItem(DEFERRED_UPDATE_KEY)
  } catch {
    return null
  }
}

function setDeferredVersion(version: string) {
  try {
    localStorage.setItem(DEFERRED_UPDATE_KEY, version)
  } catch {
    void 0
  }
}

function clearDeferredVersion() {
  try {
    localStorage.removeItem(DEFERRED_UPDATE_KEY)
  } catch {
    void 0
  }
}

export const useUpdaterStore = defineStore('updater', () => {
  const checking = ref(false)
  const installing = ref(false)
  const updateAvailable = ref(false)
  const updateInfo = ref<{ version: string; body: string; date: string } | null>(null)
  const error = ref<string | null>(null)
  const downloadProgress = ref(0)
  let pendingUpdate: TauriUpdate | null = null

  async function checkForUpdates(manual = false) {
    const settingsStore = useSettingsStore()
    const notifications = useNotificationsStore()

    if (!manual && !settingsStore.settings.globalSettings.auto_update_enabled) {
      return
    }

    if (checking.value || installing.value) return
    checking.value = true
    error.value = null
    downloadProgress.value = 0

    try {
      const update = await check() as TauriUpdate | null

      if (!update) {
        if (manual) {
          notifications.notifyInfo('Up to date', 'You are already running the latest version.')
        }
        return
      }

      pendingUpdate = update
      updateAvailable.value = true
      updateInfo.value = {
        version: update.version,
        body: update.body || '',
        date: update.date || new Date().toISOString()
      }

      if (!manual) {
        if (getDeferredVersion() !== update.version) {
          notifications.notifyInfo('Update available', `Pretupid ${update.version} is ready to install from Settings.`)
        }
        return
      }

      await installUpdate(update)
    } catch (e) {
      error.value = String(e)
      clearPendingNotes()
      if (manual) {
        notifications.notifyError('Update check failed', String(e))
      }
    } finally {
      checking.value = false
    }
  }

  async function installUpdate(update: TauriUpdate) {
    const splashStore = useSplashStore()
    const notifications = useNotificationsStore()

    installing.value = true
    splashStore.showUpdate(update.version)

    const notes = await fetchReleaseNotes(update.version)
    if (notes && !notes.body && update.body) {
      notes.body = update.body
    }
    if (notes) {
      savePendingNotes(notes)
    }
    clearDeferredVersion()

    try {
      let total = 0
      let received = 0
      await update.downloadAndInstall((event: DownloadEvent) => {
        if (event.event === 'Started') {
          total = event.data?.contentLength ?? 0
          received = 0
          downloadProgress.value = 0
          splashStore.updateProgress(0)
        } else if (event.event === 'Progress') {
          received += event.data?.chunkLength ?? 0
          if (total > 0) {
            const percent = Math.min(99, Math.floor((received / total) * 100))
            downloadProgress.value = percent
            splashStore.updateProgress(percent)
          }
        } else if (event.event === 'Finished') {
          downloadProgress.value = 100
          splashStore.updateProgress(100)
        }
      })

      notifications.notifySuccess(
        'Update installed',
        'Restart Pretupid to finish applying the update.'
      )
    } catch (e) {
      error.value = String(e)
      clearPendingNotes()
      splashStore.hide()
      notifications.notifyError('Update failed', String(e))
    } finally {
      installing.value = false
      downloadProgress.value = 0
    }
  }

  async function installPending() {
    if (installing.value) return
    if (pendingUpdate) {
      await installUpdate(pendingUpdate)
    } else {
      await checkForUpdates(true)
    }
  }

  function deferUpdate() {
    if (updateInfo.value) {
      setDeferredVersion(updateInfo.value.version)
      updateAvailable.value = false
      updateInfo.value = null
    }
  }

  function takePendingReleaseNotes(): PendingReleaseNotes | null {
    try {
      const raw = localStorage.getItem(PENDING_NOTES_KEY)
      if (!raw) return null
      localStorage.removeItem(PENDING_NOTES_KEY)
      const parsed = JSON.parse(raw) as PendingReleaseNotes
      if (!parsed || typeof parsed.version !== 'string') return null
      return parsed
    } catch {
      return null
    }
  }

  return {
    checking,
    installing,
    updateAvailable,
    updateInfo,
    error,
    downloadProgress,
    checkForUpdates,
    installPending,
    deferUpdate,
    takePendingReleaseNotes
  }
})