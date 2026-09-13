import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const MIN_VISIBLE_MS = 650

export const useSplashStore = defineStore('splash', () => {
  const visible = ref(true)
  const message = ref('Starting Pretupid')
  const progress = ref<number | null>(null)
  const startedAt = ref<number>(Date.now())
  const finished = ref(false)

  const determinate = computed(() => progress.value !== null)

  function showStartup() {
    visible.value = true
    finished.value = false
    message.value = 'Starting Pretupid'
    progress.value = null
    startedAt.value = Date.now()
  }

  function finishStartup() {
    if (finished.value) return
    finished.value = true

    if (progress.value !== null) return

    const elapsed = Date.now() - startedAt.value
    const remaining = Math.max(0, MIN_VISIBLE_MS - elapsed)

    if (remaining === 0) {
      visible.value = false
      return
    }

    setTimeout(() => {
      if (progress.value === null) {
        visible.value = false
      }
    }, remaining)
  }

  function showUpdate(version: string) {
    visible.value = true
    message.value = `Downloading Pretupid ${version}`
    progress.value = 0
  }

  function updateProgress(value: number) {
    progress.value = Math.min(100, Math.max(0, value))
  }

  function showError(text: string) {
    visible.value = true
    message.value = text
    progress.value = null
  }

  function setMessage(text: string) {
    message.value = text
  }

  function hide() {
    visible.value = false
    progress.value = null
  }

  return {
    visible,
    message,
    progress,
    determinate,
    startedAt,
    showStartup,
    finishStartup,
    showUpdate,
    updateProgress,
    showError,
    setMessage,
    hide
  }
})