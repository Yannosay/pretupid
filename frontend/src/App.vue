<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import AppLayout from '@/components/AppLayout.vue'
import SplashOverlay from '@/components/SplashOverlay.vue'
import { useAuthStore } from '@/stores/auth'
import { useInstanceStore } from '@/stores/instances'
import { useProfileStore } from '@/stores/profile'
import { useSettingsStore } from '@/stores/settings'
import { useSplashStore } from '@/stores/splash'
import { useUpdaterStore } from '@/stores/updater'
import { useLaunchStore } from '@/stores/launch'
import { useNotificationsStore } from '@/stores/notifications'
import {
  getInstance,
  getInstanceSettings,
  getGlobalSettings,
  launchInstance
} from '@/api'

const authStore = useAuthStore()
const instanceStore = useInstanceStore()
const profileStore = useProfileStore()
const settingsStore = useSettingsStore()
const splashStore = useSplashStore()
const updaterStore = useUpdaterStore()
const launchStore = useLaunchStore()
const notifications = useNotificationsStore()

const unlisteners: UnlistenFn[] = []

async function launchInstanceById(instanceId: string, sourceLabel: string) {
  try {
    const instance = await getInstance(instanceId)
    if (!instance) {
      notifications.notifyError('Launch failed', 'Instance no longer exists.')
      return
    }

    const storedSettings = await getInstanceSettings(instanceId)
    const globalSettings = await getGlobalSettings()

    const launchSettings = {
      ...storedSettings,
      auto_install_java: globalSettings.auto_install_java,
      java_path: storedSettings.java_path ?? globalSettings.default_java_path
    }

    launchStore.reset(instanceId)
    notifications.notifyInfo('Launching', `Starting ${instance.name} (${sourceLabel}).`)
    await launchInstance(instanceId, instance.minecraft_version, launchSettings)
  } catch (e) {
    notifications.notifyError('Launch failed', String(e))
  }
}

async function registerListeners() {
  unlisteners.push(
    await listen<string>('tray-panel://launch', async (event) => {
      const instanceId = event.payload
      if (typeof instanceId !== 'string' || instanceId.length === 0) return
      await launchInstanceById(instanceId, 'tray')
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
    await listen<{ instance_id: string; message: string }>(
      'modpack://progress',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return
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
    await listen<{ instance_id: string; minecraft_version: string }>(
      'modpack://instance-updated',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return
        instanceStore.load().catch(() => void 0)
      }
    )
  )

  unlisteners.push(
    await listen<{ instance_id: string; name: string }>(
      'modpack://complete',
      (event) => {
        const payload = event.payload
        if (!payload || typeof payload.instance_id !== 'string') return
        const existing = notifications.getInstallNotification(payload.instance_id)
        if (existing) {
          notifications.update(existing, {
            kind: 'success',
            title: 'Modpack installed',
            message: `${payload.name} is ready to launch.`,
            autoDismissMs: 6000
          })
          notifications.untrackInstall(payload.instance_id)
        } else {
          notifications.notifySuccess(
            'Modpack installed',
            `${payload.name} is ready to launch.`
          )
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

  if (authStore.authenticated && authStore.profile?.id) {
    instanceStore.load().catch(() => void 0)
    profileStore.load(authStore.profile.id).catch(() => void 0)
  }

  launchStore.initListeners().catch(() => void 0)
  updaterStore.checkForUpdates().catch(() => void 0)

  await registerListeners()
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
</template>