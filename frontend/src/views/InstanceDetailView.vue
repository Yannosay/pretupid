<template>
  <div class="instance-detail">
    <header class="instance-detail__header">
      <button class="btn btn--ghost" @click="goBack">← Back</button>
      <h1 class="instance-detail__title">{{ instance?.name || 'Loading...' }}</h1>
      <button
        class="btn btn--danger"
        :disabled="!instance"
        @click="deleteConfirmOpen = true"
      >
        Delete
      </button>
      <button
        v-if="!isBusy"
        class="btn"
        :disabled="!instance || (launchStore.anyRunning && !launchState.running)"
        @click="handleLaunch"
      >
        Launch
      </button>
      <button
        v-else
        class="btn btn--warning"
        @click="handleCancelLaunch"
      >
        {{ launchState.running ? 'Running...' : 'Cancel' }}
      </button>
    </header>

    <div v-if="error" class="detail-error">
      <span>{{ error }}</span>
      <button class="btn btn--ghost error-copy" @click="copyError">Copy</button>
    </div>

    <div v-if="saveMessage" class="save-message">{{ saveMessage }}</div>

    <div v-if="deleteConfirmOpen" class="delete-confirm">
      <p>Are you sure you want to delete "{{ instance?.name }}"? This cannot be undone.</p>
      <div class="delete-confirm__actions">
        <button class="btn btn--danger" @click="handleDelete">Delete</button>
        <button class="btn btn--ghost" @click="deleteConfirmOpen = false">Cancel</button>
      </div>
    </div>

    <div v-if="instance" class="instance-detail__body">
      <section class="instance-detail__section">
        <h3>Settings</h3>
        <div class="settings-grid">
          <div class="form-group">
            <label class="form-label">Memory Min (MB)</label>
            <input v-model.number="settings.memory_min_mb" type="number" class="input" min="256" step="128" />
          </div>
          <div class="form-group">
            <label class="form-label">Memory Max (MB)</label>
            <input v-model.number="settings.memory_max_mb" type="number" class="input" min="512" step="128" />
          </div>
          <div class="form-group">
            <label class="form-label">Width</label>
            <input v-model.number="settings.resolution_width" type="number" class="input" min="800" />
          </div>
          <div class="form-group">
            <label class="form-label">Height</label>
            <input v-model.number="settings.resolution_height" type="number" class="input" min="600" />
          </div>
          <div class="form-group form-group--checkbox">
            <label class="form-label">Fullscreen</label>
            <label class="checkbox-label">
              <input v-model="settings.fullscreen" type="checkbox" class="checkbox" />
              <span>{{ settings.fullscreen ? 'Enabled' : 'Disabled' }}</span>
            </label>
          </div>
        </div>
        <button class="btn btn--secondary" @click="saveSettings">Save Settings</button>
      </section>

      <section v-if="launchState.progress" class="instance-detail__section">
        <h3>Download Progress</h3>
        <div class="progress-bar">
          <div
            class="progress-bar__fill"
            :style="{ width: progressPercent + '%' }"
          />
        </div>
        <p class="text-secondary">{{ progressPercent }}%</p>
        <p class="text-secondary">{{ launchState.progress.message }}</p>
      </section>

      <section v-if="launchState.logs.length > 0" class="instance-detail__section">
        <h3>Console</h3>
        <div class="console">
          <div v-for="(line, i) in launchState.logs" :key="i" class="console__line">
            {{ line }}
          </div>
        </div>
        <button class="btn btn--ghost console-copy" @click="copyConsole">Copy</button>
      </section>

      <section v-if="launchState.exitCode !== null" class="instance-detail__section">
        <h3>Game Exited</h3>
        <p class="text-secondary">Exit code: {{ launchState.exitCode }}</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getInstance, getInstanceSettings, saveInstanceSettings, launchInstance, cancelLaunch, getGlobalSettings } from '@/api'
import { useLaunchStore } from '@/stores/launch'
import { useAuthStore } from '@/stores/auth'
import { useInstanceStore } from '@/stores/instances'
import type { Instance, LaunchSettings } from '@/types'

const route = useRoute()
const router = useRouter()
const launchStore = useLaunchStore()
const authStore = useAuthStore()
const instanceStore = useInstanceStore()

const instance = ref<Instance | null>(null)
const settings = ref<LaunchSettings>({
  memory_min_mb: 512,
  memory_max_mb: 4096,
  resolution_width: 1280,
  resolution_height: 720,
  fullscreen: false,
  java_path: null,
  auto_install_java: true,
  extra_jvm_args: [],
  extra_game_args: []
})
const launching = ref(false)
const error = ref<string | null>(null)
const saveMessage = ref<string | null>(null)
const deleteConfirmOpen = ref(false)

const instanceId = computed(() => instance.value?.id || '')

const launchState = computed(() => {
  if (!instanceId.value) {
    return { progress: null, logs: [], running: false, pid: null, exitCode: null }
  }
  return launchStore.getState(instanceId.value)
})

const isBusy = computed(() => launching.value || launchState.value.running)

const progressPercent = computed(() => {
  if (!launchState.value.progress || launchState.value.progress.total === 0) return 0
  return Math.round((launchState.value.progress.current / launchState.value.progress.total) * 100)
})

onMounted(async () => {
  await launchStore.initListeners()
  try {
    const id = route.params.id as string
    instance.value = await getInstance(id)
    if (instance.value) {
      launchStore.ensureState(id)
      const loadedSettings = await getInstanceSettings(id)
      settings.value = {
        ...loadedSettings,
        auto_install_java: true
      }
      const gs = await getGlobalSettings()
      settings.value.auto_install_java = gs.auto_install_java
      if (loadedSettings.java_path) {
        settings.value.java_path = loadedSettings.java_path
      } else if (gs.default_java_path) {
        settings.value.java_path = gs.default_java_path
      }
      if (launchStore.getState(id).running) {
        launching.value = true
      }
    }
  } catch (e) {
    error.value = String(e)
  }
})

watch(instanceId, (newId) => {
  launching.value = false
  if (newId && launchStore.getState(newId).running) {
    launching.value = true
  }
})

watch(() => launchState.value.running, (isRunning) => {
  launching.value = isRunning
})

function goBack() {
  router.push({ name: 'library' })
}

async function saveSettings() {
  if (!instance.value) return
  error.value = null
  saveMessage.value = null
  try {
    await saveInstanceSettings(instance.value.id, settings.value)
    saveMessage.value = 'Settings saved!'
    setTimeout(() => {
      saveMessage.value = null
    }, 3000)
  } catch (e) {
    error.value = String(e)
  }
}

async function handleDelete() {
  if (!instance.value) return
  error.value = null
  try {
    await instanceStore.remove(instance.value.id)
    router.push({ name: 'library' })
  } catch (e) {
    error.value = String(e)
    deleteConfirmOpen.value = false
  }
}

async function handleLaunch() {
  if (!instance.value || !authStore.authenticated) return
  launching.value = true
  error.value = null
  saveMessage.value = null
  launchStore.reset(instance.value.id)
  try {
    await saveInstanceSettings(instance.value.id, settings.value)
    await launchInstance(instance.value.id, instance.value.minecraft_version, settings.value)
  } catch (e) {
    error.value = String(e)
  } finally {
    launching.value = false
  }
}

async function handleCancelLaunch() {
  if (!instance.value) return
  error.value = null
  try {
    await cancelLaunch(instance.value.id)
    launching.value = false
    launchStore.reset(instance.value.id)
  } catch (e) {
    error.value = String(e)
  }
}

function copyError() {
  if (error.value) {
    navigator.clipboard.writeText(error.value)
  }
}

function copyConsole() {
  const text = launchState.value.logs.join('\n')
  if (text) {
    navigator.clipboard.writeText(text)
  }
}
</script>

<style scoped lang="scss">
.instance-detail__header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
}

.instance-detail__title {
  flex: 1;
  font-size: 2rem;
}

.detail-error {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  margin-bottom: 1.5rem;
  background-color: #e74c3c;
  color: white;
  border-radius: var(--edge-radius);
  border: 2px solid #c0392b;
}

.error-copy {
  font-size: 0.6rem;
  padding: 0.4rem 0.8rem;
}

.save-message {
  padding: 0.75rem 1rem;
  margin-bottom: 1.5rem;
  background-color: var(--accent);
  color: var(--accent-text);
  border-radius: var(--edge-radius);
  border: 2px solid var(--accent);
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
}

.delete-confirm {
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid #e74c3c;
  border-radius: var(--edge-radius);
}

.delete-confirm p {
  margin-bottom: 1rem;
}

.delete-confirm__actions {
  display: flex;
  gap: 0.75rem;
}

.instance-detail__body {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.instance-detail__section {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 1rem;
}

.form-group {
  margin-bottom: 0.5rem;
}

.form-group--checkbox {
  display: flex;
  flex-direction: column;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-primary);
}

.checkbox {
  width: 20px;
  height: 20px;
  cursor: pointer;
  accent-color: var(--accent);
}

.progress-bar {
  height: 20px;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
  margin-bottom: 0.75rem;
}

.progress-bar__fill {
  height: 100%;
  background-color: var(--accent);
  transition: width var(--transition-normal);
}

.console {
  background-color: var(--bg-primary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1rem;
  max-height: 300px;
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
}

.console__line {
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.btn--warning {
  background-color: #f39c12;
  color: white;
  border: 2px solid #e67e22;
}

.console-copy {
  margin-top: 0.75rem;
  font-size: 0.6rem;
  padding: 0.4rem 0.8rem;
}
</style>