<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import { invoke } from '@tauri-apps/api/core'
import AppLayout from '@/components/AppLayout.vue'
import SplashOverlay from '@/components/SplashOverlay.vue'
import WhatsNewOverlay from '@/components/WhatsNewOverlay.vue'
import { useAuthStore } from '@/stores/auth'
import { useInstanceStore } from '@/stores/instances'
import { useProfileStore } from '@/stores/profile'
import { useSettingsStore } from '@/stores/settings'
import { useSplashStore } from '@/stores/splash'
import { useUpdaterStore } from '@/stores/updater'
import { useLaunchStore } from '@/stores/launch'
import { useNotificationsStore } from '@/stores/notifications'
import { useInstallsStore, type InstallStatus } from '@/stores/installs'
import { getInstance } from '@/api'
import { loadBlockTextures } from '@/utils/instanceIcon'

interface DeepLinkAction {
  type: string
  theme?: string | null
  accent?: string | null
  edge?: string | null
}

const authStore = useAuthStore()
const instanceStore = useInstanceStore()
const profileStore = useProfileStore()
const settingsStore = useSettingsStore()
const splashStore = useSplashStore()
const updaterStore = useUpdaterStore()
const launchStore = useLaunchStore()
const notifications = useNotificationsStore()
const installs = useInstallsStore()

const unlisteners: UnlistenFn[] = []

function applyDeepLinkAction(payload: DeepLinkAction) {
  if (!payload || payload.type !== 'settings') return

  if (payload.theme === 'dark' || payload.theme === 'light') {
    settingsStore.setTheme(payload.theme)
  }
  if (typeof payload.accent === 'string' && /^#?[0-9a-fA-F]{6}$/.test(payload.accent)) {
    settingsStore.setAccentColor(payload.accent.startsWith('#') ? payload.accent : `#${payload.accent}`)
  }
  if (typeof payload.edge === 'string') {
    const parsed = parseInt(payload.edge, 10)
    if (!Number.isNaN(parsed) && parsed >= 0 && parsed <= 24) {
      settingsStore.setEdgeRadius(parsed)
    }
  }

  notifications.notifySuccess(
    'Settings synced from website',
    'Your theme, accent, and corner preferences have been applied.'
  )
}

async function drainPendingDeepLinks() {
  try {
    const pending = await invoke<DeepLinkAction[]>('take_pending_deep_links')
    for (const action of pending) {
      applyDeepLinkAction(action)
    }
  } catch {
    void 0
  }
}

async function launchInstanceById(instanceId: string) {
  if (installs.isInstalling(instanceId)) {
    notifications.notifyWarning('Installation in progress', 'Wait for the modpack to finish installing before launching.')
    return
  }
  try {
    const instance = await getInstance(instanceId)
    if (!instance) {
      notifications.notifyError('Launch failed', 'Instance no longer exists.')
      return
    }
    notifications.notifyInfo('Launching', `Starting ${instance.name}.`)
    await launchStore.play(instanceId)
  } catch (e) {
    notifications.notifyError('Launch failed', String(e))
  }
}

async function registerListeners() {
  unlisteners.push(
    await listen<string>('tray-panel://launch', async (event) => {
      const instanceId = event.payload
      if (typeof instanceId !== 'string' || instanceId.length === 0) return
      await launchInstanceById(instanceId)
    })
  )

  unlisteners.push(
    await listen<DeepLinkAction>('deep-link://action', (event) => {
      applyDeepLinkAction(event.payload)
    })
  )

  unlisteners.push(
    await listen<{ instance_id: string; message: string }>(
      'launch://failed',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return
        const instance = instanceStore.instances.find((i) => i.id === payload.instance_id)
        const name = instance?.name ?? 'Instance'
        notifications.notifyError(`Launch failed: ${name}`, payload.message)
      }
    )
  )

  unlisteners.push(
    await listen<{ instance_id: string; status: string; message: string }>(
      'modpack://progress',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return

        const status = payload.status as InstallStatus
        if (!installs.getState(payload.instance_id)) {
          installs.begin(payload.instance_id, payload.message)
        }
        installs.update(payload.instance_id, status, payload.message)

        const existing = notifications.getInstallNotification(payload.instance_id)
        if (existing) {
          notifications.update(existing, { message: payload.message })
        } else {
          const id = notifications.push({
            kind: 'info',
            title: 'Installing modpack',
            message: payload.message,
            autoDismissMs: null
          })
          notifications.trackInstall(payload.instance_id, id)
        }
      }
    )
  )

  unlisteners.push(
    await listen('modpack://instance-updated', () => {
      instanceStore.load().catch(() => void 0)
    })
  )

  unlisteners.push(
    await listen<{ instance_id: string; name: string; missing?: number }>(
      'modpack://complete',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return

        installs.complete(payload.instance_id)

        const missing = typeof payload.missing === 'number' ? payload.missing : 0
        const kind = missing > 0 ? 'warning' : 'success'
        const message =
          missing > 0
            ? `${payload.name} is installed, but ${missing} file${missing === 1 ? '' : 's'} could not be downloaded yet. Press Play to try again.`
            : `${payload.name} is ready to launch.`
        const existing = notifications.getInstallNotification(payload.instance_id)
        if (existing) {
          notifications.update(existing, {
            kind,
            title: 'Modpack installed',
            message,
            autoDismissMs: missing > 0 ? null : 6000
          })
          notifications.untrackInstall(payload.instance_id)
        } else if (missing > 0) {
          notifications.notifyWarning('Modpack installed', message)
        } else {
          notifications.notifySuccess('Modpack installed', message)
        }
        instanceStore.load().catch(() => void 0)
      }
    )
  )

  unlisteners.push(
    await listen<{ instance_id: string; message: string }>(
      'modpack://error',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return

        installs.fail(payload.instance_id, payload.message)

        const existing = notifications.getInstallNotification(payload.instance_id)
        if (existing) {
          notifications.update(existing, {
            kind: 'error',
            title: 'Modpack install failed',
            message: payload.message,
            autoDismissMs: null
          })
          notifications.untrackInstall(payload.instance_id)
        } else {
          notifications.notifyError('Modpack install failed', payload.message)
        }
        instanceStore.load().catch(() => void 0)
      }
    )
  )
}

onMounted(async () => {
  splashStore.setMessage('Loading settings')
  try {
    settingsStore.loadLocalSettings()
  } catch {
    void 0
  }

  splashStore.setMessage('Authenticating')
  try {
    await authStore.check()
  } catch {
    void 0
  }

  splashStore.finishStartup()

  await registerListeners()
  await drainPendingDeepLinks()

  if (authStore.authenticated && authStore.profile?.id) {
    instanceStore.load().catch(() => void 0)
    profileStore.load(authStore.profile.id).catch(() => void 0)
  }

  launchStore.initListeners().catch(() => void 0)
  loadBlockTextures().catch(() => void 0)
  updaterStore.checkForUpdates().catch(() => void 0)
})

onBeforeUnmount(() => {
  for (const unlisten of unlisteners) {
    try {
      unlisten()
    } catch {
      void 0
    }
  }
  unlisteners.length = 0
})
</script>

<template>
  <AppLayout />
  <SplashOverlay />
  <WhatsNewOverlay />
</template>