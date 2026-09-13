import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type NotificationKind = 'info' | 'success' | 'warning' | 'error'

export interface AppNotification {
  id: string
  kind: NotificationKind
  title: string
  message: string
  createdAt: number
  read: boolean
  autoDismissMs: number | null
  actionLabel?: string
  actionHandler?: () => void
}

interface PushOptions {
  kind: NotificationKind
  title: string
  message: string
  autoDismissMs?: number | null
  actionLabel?: string
  actionHandler?: () => void
}

interface UpdatePatch {
  kind?: NotificationKind
  title?: string
  message?: string
  autoDismissMs?: number | null
}

function createId(): string {
  return `n_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
}

const MAX_ITEMS = 50

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<AppNotification[]>([])
  const open = ref(false)
  const timers = new Map<string, number>()
  const installNotifications = ref<Record<string, string>>({})

  const unreadCount = computed(
    () => items.value.reduce((acc, n) => (n.read ? acc : acc + 1), 0)
  )
  const hasItems = computed(() => items.value.length > 0)

  function clearTimer(id: string) {
    const handle = timers.get(id)
    if (handle !== undefined) {
      window.clearTimeout(handle)
      timers.delete(id)
    }
  }

  function armTimer(id: string, ms: number) {
    clearTimer(id)
    const handle = window.setTimeout(() => dismiss(id), ms)
    timers.set(id, handle)
  }

  function push(options: PushOptions): string {
    const id = createId()
    const item: AppNotification = {
      id,
      kind: options.kind,
      title: options.title,
      message: options.message,
      createdAt: Date.now(),
      read: false,
      autoDismissMs: options.autoDismissMs ?? null,
      actionLabel: options.actionLabel,
      actionHandler: options.actionHandler
    }

    items.value = [item, ...items.value].slice(0, MAX_ITEMS)

    if (item.autoDismissMs && item.autoDismissMs > 0) {
      armTimer(id, item.autoDismissMs)
    }

    return id
  }

  function update(id: string, patch: UpdatePatch) {
    const index = items.value.findIndex((n) => n.id === id)
    if (index < 0) return

    const previous = items.value[index]
    const next: AppNotification = {
      ...previous,
      kind: patch.kind ?? previous.kind,
      title: patch.title ?? previous.title,
      message: patch.message ?? previous.message,
      autoDismissMs:
        patch.autoDismissMs === undefined ? previous.autoDismissMs : patch.autoDismissMs
    }

    const copy = [...items.value]
    copy[index] = next
    items.value = copy

    if (patch.autoDismissMs !== undefined) {
      if (patch.autoDismissMs && patch.autoDismissMs > 0) {
        armTimer(id, patch.autoDismissMs)
      } else {
        clearTimer(id)
      }
    }
  }

  function notifyInfo(title: string, message: string, autoDismissMs = 5000) {
    return push({ kind: 'info', title, message, autoDismissMs })
  }

  function notifySuccess(title: string, message: string, autoDismissMs = 4500) {
    return push({ kind: 'success', title, message, autoDismissMs })
  }

  function notifyWarning(title: string, message: string, autoDismissMs = 8000) {
    return push({ kind: 'warning', title, message, autoDismissMs })
  }

  function notifyError(title: string, message: string, autoDismissMs: number | null = null) {
    return push({ kind: 'error', title, message, autoDismissMs })
  }

  function dismiss(id: string) {
    clearTimer(id)
    items.value = items.value.filter((n) => n.id !== id)
  }

  function markAllRead() {
    items.value = items.value.map((n) => ({ ...n, read: true }))
  }

  function clearAll() {
    for (const handle of timers.values()) {
      window.clearTimeout(handle)
    }
    timers.clear()
    items.value = []
  }

  function toggle() {
    open.value = !open.value
    if (open.value) {
      markAllRead()
    }
  }

  function close() {
    open.value = false
  }

  function trackInstall(instanceId: string, notificationId: string) {
    installNotifications.value = {
      ...installNotifications.value,
      [instanceId]: notificationId
    }
  }

  function untrackInstall(instanceId: string) {
    const copy = { ...installNotifications.value }
    delete copy[instanceId]
    installNotifications.value = copy
  }

  function getInstallNotification(instanceId: string): string | undefined {
    return installNotifications.value[instanceId]
  }

  return {
    items,
    open,
    unreadCount,
    hasItems,
    installNotifications,
    push,
    update,
    notifyInfo,
    notifySuccess,
    notifyWarning,
    notifyError,
    dismiss,
    markAllRead,
    clearAll,
    toggle,
    close,
    trackInstall,
    untrackInstall,
    getInstallNotification
  }
})