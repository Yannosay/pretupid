import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listen } from '@tauri-apps/api/event'
import type { LaunchProgress, LaunchLog, LaunchStarted, LaunchExited } from '@/types'

export interface LaunchState {
  progress: LaunchProgress | null
  logs: string[]
  running: boolean
  pid: number | null
  exitCode: number | null
  lastError: string | null
}

function createEmptyState(): LaunchState {
  return {
    progress: null,
    logs: [],
    running: false,
    pid: null,
    exitCode: null,
    lastError: null
  }
}

export const useLaunchStore = defineStore('launch', () => {
  const states = ref<Record<string, LaunchState>>({})
  const activeInstanceId = ref<string | null>(null)
  let listenersRegistered = false
  let initPromise: Promise<void> | null = null

  function ensureState(instanceId: string): LaunchState {
    const existing = states.value[instanceId]
    if (existing) return existing
    const created = createEmptyState()
    states.value = { ...states.value, [instanceId]: created }
    return created
  }

  function getState(instanceId: string): LaunchState {
    return states.value[instanceId] ?? createEmptyState()
  }

  async function initListeners(): Promise<void> {
    if (listenersRegistered) return
    if (initPromise) return initPromise

    initPromise = (async () => {
      await listen<LaunchProgress>('launch://progress', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        ensureState(payload.instance_id)
        const current = states.value[payload.instance_id]
        current.progress = {
          instance_id: payload.instance_id,
          current: payload.current,
          total: payload.total,
          message: payload.message
        }
        current.lastError = null
        activeInstanceId.value = payload.instance_id
      })

      await listen<LaunchLog>('launch://log', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        ensureState(payload.instance_id)
        const current = states.value[payload.instance_id]
        current.logs.push(payload.line)
        if (current.logs.length > 500) {
          current.logs.splice(0, current.logs.length - 500)
        }
      })

      await listen<LaunchStarted>('launch://started', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        ensureState(payload.instance_id)
        const current = states.value[payload.instance_id]
        current.running = true
        current.pid = payload.pid
        current.exitCode = null
        current.lastError = null
        activeInstanceId.value = payload.instance_id
      })

      await listen<{ instance_id: string; message: string }>(
        'launch://failed',
        (event) => {
          const payload = event.payload
          if (!payload?.instance_id) return
          ensureState(payload.instance_id)
          const current = states.value[payload.instance_id]
          current.lastError = payload.message
        }
      )

      await listen<LaunchExited>('launch://exited', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        ensureState(payload.instance_id)
        const current = states.value[payload.instance_id]
        current.running = false
        current.pid = null
        current.exitCode = payload.code
        current.progress = null
        if (activeInstanceId.value === payload.instance_id) {
          activeInstanceId.value = null
        }
      })

      listenersRegistered = true
    })()

    return initPromise
  }

  function reset(instanceId: string) {
    ensureState(instanceId)
    const current = states.value[instanceId]
    current.progress = null
    current.logs = []
    current.running = false
    current.pid = null
    current.exitCode = null
    current.lastError = null
  }

  const activeState = computed<LaunchState | null>(() => {
    if (!activeInstanceId.value) return null
    return states.value[activeInstanceId.value] ?? null
  })

  const anyRunning = computed(() => {
    return Object.values(states.value).some((s) => s.running)
  })

  function isRunning(instanceId: string): boolean {
    return states.value[instanceId]?.running === true
  }

  return {
    states,
    activeInstanceId,
    activeState,
    anyRunning,
    initListeners,
    reset,
    getState,
    ensureState,
    isRunning
  }
})