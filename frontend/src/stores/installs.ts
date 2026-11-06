import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type InstallStatus =
  | 'downloading_archive'
  | 'extracting'
  | 'resolving'
  | 'installing_files'
  | 'downloading_mods'
  | 'copying_overrides'
  | 'complete'
  | 'error'

export interface InstallState {
  instanceId: string
  status: InstallStatus
  message: string
  startedAt: number
  error: string | null
}

const STATUS_LABELS: Record<InstallStatus, string> = {
  downloading_archive: 'Downloading archive',
  extracting: 'Extracting',
  resolving: 'Resolving metadata',
  installing_files: 'Installing files',
  downloading_mods: 'Downloading mods',
  copying_overrides: 'Copying overrides',
  complete: 'Complete',
  error: 'Failed'
}

const COMPLETE_LINGER_MS = 2500

export const useInstallsStore = defineStore('installs', () => {
  const states = ref<Record<string, InstallState>>({})
  const cleanupTimers = new Map<string, number>()

  const activeIds = computed(() =>
    Object.values(states.value)
      .filter((s) => s.status !== 'complete' && s.status !== 'error')
      .map((s) => s.instanceId)
  )

  const hasActive = computed(() => activeIds.value.length > 0)

  function getState(instanceId: string): InstallState | null {
    return states.value[instanceId] ?? null
  }

  function isInstalling(instanceId: string): boolean {
    const state = states.value[instanceId]
    if (!state) return false
    return state.status !== 'complete' && state.status !== 'error'
  }

  function hasFailed(instanceId: string): boolean {
    const state = states.value[instanceId]
    return state?.status === 'error'
  }

  function clearCleanup(instanceId: string) {
    const handle = cleanupTimers.get(instanceId)
    if (handle !== undefined) {
      window.clearTimeout(handle)
      cleanupTimers.delete(instanceId)
    }
  }

  function begin(instanceId: string, message: string) {
    clearCleanup(instanceId)
    states.value = {
      ...states.value,
      [instanceId]: {
        instanceId,
        status: 'downloading_archive',
        message,
        startedAt: Date.now(),
        error: null
      }
    }
  }

  function update(instanceId: string, status: InstallStatus, message: string) {
    const existing = states.value[instanceId]
    const next: InstallState = {
      instanceId,
      status,
      message,
      startedAt: existing?.startedAt ?? Date.now(),
      error: status === 'error' ? message : null
    }
    states.value = { ...states.value, [instanceId]: next }
  }

  function complete(instanceId: string) {
    const existing = states.value[instanceId]
    if (!existing) return
    clearCleanup(instanceId)
    states.value = {
      ...states.value,
      [instanceId]: {
        ...existing,
        status: 'complete',
        message: 'Installation complete',
        error: null
      }
    }
    const handle = window.setTimeout(() => {
      const copy = { ...states.value }
      delete copy[instanceId]
      states.value = copy
      cleanupTimers.delete(instanceId)
    }, COMPLETE_LINGER_MS)
    cleanupTimers.set(instanceId, handle)
  }

  function fail(instanceId: string, message: string) {
    const existing = states.value[instanceId]
    clearCleanup(instanceId)
    states.value = {
      ...states.value,
      [instanceId]: {
        instanceId,
        status: 'error',
        message,
        startedAt: existing?.startedAt ?? Date.now(),
        error: message
      }
    }
  }

  function dismiss(instanceId: string) {
    clearCleanup(instanceId)
    const copy = { ...states.value }
    delete copy[instanceId]
    states.value = copy
  }

  function statusLabel(status: InstallStatus): string {
    return STATUS_LABELS[status]
  }

  return {
    states,
    activeIds,
    hasActive,
    getState,
    isInstalling,
    hasFailed,
    begin,
    update,
    complete,
    fail,
    dismiss,
    statusLabel
  }
})