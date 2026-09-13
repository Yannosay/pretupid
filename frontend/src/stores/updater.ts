import { defineStore } from 'pinia'
import { ref } from 'vue'
import { check } from '@tauri-apps/plugin-updater'
import { useSettingsStore } from '@/stores/settings'
import { useSplashStore } from '@/stores/splash'
import { useNotificationsStore } from '@/stores/notifications'

export const useUpdaterStore = defineStore('updater', () => {
  const checking = ref(false)
  const updateAvailable = ref(false)
  const error = ref<string | null>(null)

  async function checkForUpdates(manual = false) {
    const settingsStore = useSettingsStore()
    const splashStore = useSplashStore()
    const notifications = useNotificationsStore()

    if (!manual && !settingsStore.settings.globalSettings.auto_update_enabled) {
      return
    }

    if (checking.value) return
    checking.value = true
    error.value = null

    try {
      const update = await check()

      if (!update) {
        if (manual) {
          notifications.notifyInfo('Up to date', 'You are already running the latest version.')
        }
        return
      }

      updateAvailable.value = true
      splashStore.showUpdate(update.version)

      await update.downloadAndInstall((event) => {
        switch (event.event) {
          case 'Started':
            splashStore.updateProgress(0)
            break
          case 'Progress': {
            const data = event.data as { chunkLength?: number; contentLength?: number } | undefined
            const current = data?.chunkLength ?? 0
            const total = data?.contentLength ?? 0
            if (total > 0) {
              splashStore.updateProgress(Math.round((current / total) * 100))
            }
            break
          }
          case 'Finished':
            splashStore.updateProgress(100)
            break
        }
      })

      notifications.notifySuccess('Update installed', `Pretupid ${update.version} will run on next start.`)
    } catch (e) {
      console.error('Update check failed:', e)
      error.value = String(e)
      if (manual) {
        notifications.notifyError('Update failed', String(e))
      }
    } finally {
      checking.value = false
    }
  }

  return {
    checking,
    updateAvailable,
    error,
    checkForUpdates
  }
})