import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listen } from '@tauri-apps/api/event'
import type { LaunchProgress, LaunchLog, LaunchStarted, LaunchExited, CrashInfo } from '@/types'
import { cancelLaunch, launchInstance, listRunningInstances, prepareInstance } from '@/api'

export type LaunchPhase = 'idle' | 'preparing' | 'running' | 'installing'

export interface LaunchState {
  progress: LaunchProgress | null
  logs: string[]
  running: boolean
  busy: boolean
  phase: LaunchPhase
  pid: number | null
  exitCode: number | null
  lastError: string | null
  crash: CrashInfo | null
  startedAt: number | null
}

const MAX_LOG_LINES = 2000

function createEmptyState(): LaunchState {
  return {
    progress: null,
    logs: [],
    running: false,
    busy: false,
    phase: 'idle',
    pid: null,
    exitCode: null,
    lastError: null,
    crash: null,
    startedAt: null
  }
}

export const useLaunchStore = defineStore('launch', () => {
  const states = ref<Record<string, LaunchState>>({})
  const activeInstanceId = ref<string | null>(null)
  let initPromise: Promise<void> | null = null

  function ensureState(instanceId: string): LaunchState {
    const existing = states.value[instanceId]
    if (existing) return existing
    states.value = { ...states.value, [instanceId]: createEmptyState() }
    return states.value[instanceId]
  }

  function getState(instanceId: string): LaunchState {
    return states.value[instanceId] ?? createEmptyState()
  }

  async function initListeners(): Promise<void> {
    if (initPromise) return initPromise

    initPromise = (async () => {
      await listen<LaunchProgress>('launch://progress', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        const current = ensureState(payload.instance_id)
        current.progress = { ...payload }
        current.busy = true
        if (current.phase === 'idle') current.phase = 'preparing'
        activeInstanceId.value = payload.instance_id
      })

      await listen<LaunchLog>('launch://log', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        const current = ensureState(payload.instance_id)
        current.logs.push(payload.line)
        if (current.logs.length > MAX_LOG_LINES) {
          current.logs.splice(0, current.logs.length - MAX_LOG_LINES)
        }
      })

      await listen<LaunchStarted>('launch://started', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        const current = ensureState(payload.instance_id)
        current.running = true
        current.busy = true
        current.phase = 'running'
        current.pid = payload.pid
        current.exitCode = null
        current.progress = null
        current.startedAt = Date.now()
        activeInstanceId.value = payload.instance_id
      })

      await listen<{ instance_id: string; message: string }>('launch://failed', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        ensureState(payload.instance_id).lastError = payload.message
      })

      await listen<CrashInfo>('launch://crash', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        ensureState(payload.instance_id).crash = payload
      })

      await listen<LaunchExited>('launch://exited', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        const current = ensureState(payload.instance_id)
        current.running = false
        current.busy = false
        current.phase = 'idle'
        current.pid = null
        current.exitCode = payload.code
        current.progress = null
        current.startedAt = null
        if (activeInstanceId.value === payload.instance_id) activeInstanceId.value = null
      })

      await listen<{ instance_id: string; ok: boolean; message: string | null }>('launch://prepared', (event) => {
        const payload = event.payload
        if (!payload?.instance_id) return
        const current = ensureState(payload.instance_id)
        current.busy = false
        current.phase = 'idle'
        current.progress = null
        if (!payload.ok && payload.message) current.lastError = payload.message
        if (activeInstanceId.value === payload.instance_id) activeInstanceId.value = null
      })

      try {
        const running = await listRunningInstances()
        for (const id of running) {
          const current = ensureState(id)
          current.running = true
          current.busy = true
          current.phase = 'running'
        }
      } catch {
        void 0
      }
    })()

    return initPromise
  }

  function reset(instanceId: string) {
    const current = ensureState(instanceId)
    Object.assign(current, createEmptyState())
  }

  async function play(instanceId: string, verifyFiles = false): Promise<void> {
    await initListeners()
    const current = ensureState(instanceId)
    if (current.busy) return
    reset(instanceId)
    const fresh = ensureState(instanceId)
    fresh.busy = true
    fresh.phase = 'preparing'
    fresh.progress = { instance_id: instanceId, current: 0, total: 0, message: 'Getting ready' }
    activeInstanceId.value = instanceId
    try {
      await launchInstance(instanceId, verifyFiles)
    } catch (e) {
      fresh.lastError = String(e)
      throw e
    } finally {
      fresh.busy = false
      fresh.running = false
      fresh.phase = 'idle'
      fresh.progress = null
    }
  }

  async function prepare(instanceId: string, verifyFiles = false): Promise<void> {
    await initListeners()
    const current = ensureState(instanceId)
    if (current.busy) return
    current.busy = true
    current.phase = 'installing'
    current.lastError = null
    current.progress = { instance_id: instanceId, current: 0, total: 0, message: verifyFiles ? 'Verifying files' : 'Downloading game files' }
    try {
      await prepareInstance(instanceId, verifyFiles)
    } catch (e) {
      current.lastError = String(e)
      throw e
    } finally {
      current.busy = false
      current.phase = 'idle'
      current.progress = null
    }
  }

  async function stop(instanceId: string): Promise<void> {
    await cancelLaunch(instanceId)
  }

  function dismissCrash(instanceId: string) {
    ensureState(instanceId).crash = null
  }

  const activeState = computed<LaunchState | null>(() => {
    if (!activeInstanceId.value) return null
    return states.value[activeInstanceId.value] ?? null
  })

  const anyRunning = computed(() => Object.values(states.value).some((s) => s.running))
  const anyBusy = computed(() => Object.values(states.value).some((s) => s.busy))

  function isRunning(instanceId: string): boolean {
    return states.value[instanceId]?.running === true
  }

  function isBusy(instanceId: string): boolean {
    return states.value[instanceId]?.busy === true
  }

  return {
    states,
    activeInstanceId,
    activeState,
    anyRunning,
    anyBusy,
    initListeners,
    reset,
    getState,
    ensureState,
    isRunning,
    isBusy,
    play,
    prepare,
    stop,
    dismissCrash
  }
})
