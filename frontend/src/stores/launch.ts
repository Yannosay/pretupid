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
}

export const useLaunchStore = defineStore('launch', () => {
  const states = ref<Record<string, LaunchState>>({})
  const activeInstanceId = ref<string | null>(null)
  let listenersRegistered = false

  function getState(instanceId: string): LaunchState {
    if (!states.value[instanceId]) {
      states.value[instanceId] = {
        progress: null,
        logs: [],
        running: false,
        pid: null,
        exitCode: null
      }
    }
    return states.value[instanceId]
  }

  function ensureState(instanceId: string) {
    getState(instanceId)
  }

  async function initListeners() {
    if (listenersRegistered) return
    listenersRegistered = true

    await listen<LaunchProgress>('launch://progress', (event) => {
      const payload = event.payload as LaunchProgress
      if (!payload.instance_id) return
      const state = getState(payload.instance_id)
      state.progress = {
        instance_id: payload.instance_id,
        current: payload.current,
        total: payload.total,
        message: payload.message
      }
      activeInstanceId.value = payload.instance_id
    })

    await listen<LaunchLog>('launch://log', (event) => {
      const payload = event.payload as LaunchLog
      if (!payload.instance_id) return
      const state = getState(payload.instance_id)
      state.logs.push(payload.line)
      if (state.logs.length > 500) {
        state.logs.shift()
      }
    })

    await listen<LaunchStarted>('launch://started', (event) => {
      const payload = event.payload as LaunchStarted
      if (!payload.instance_id) return
      const state = getState(payload.instance_id)
      state.running = true
      state.pid = payload.pid
      state.exitCode = null
      activeInstanceId.value = payload.instance_id
    })

    await listen<LaunchExited>('launch://exited', (event) => {
      const payload = event.payload as LaunchExited
      if (!payload.instance_id) return
      const state = getState(payload.instance_id)
      state.running = false
      state.pid = null
      state.exitCode = payload.code
      state.progress = null
      if (activeInstanceId.value === payload.instance_id) {
        activeInstanceId.value = null
      }
    })
  }

  function reset(instanceId: string) {
    const state = getState(instanceId)
    state.progress = null
    state.logs = []
    state.running = false
    state.pid = null
    state.exitCode = null
  }

  const activeState = computed(() => {
    if (!activeInstanceId.value) return null
    return getState(activeInstanceId.value)
  })

  const anyRunning = computed(() => {
    return Object.values(states.value).some(s => s.running)
  })

  return {
    states,
    activeInstanceId,
    activeState,
    anyRunning,
    initListeners,
    reset,
    getState,
    ensureState
  }
})