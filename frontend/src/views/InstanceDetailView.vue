<template>
  <div class="instance-detail">
    <header class="instance-detail__header">
      <button class="btn btn--ghost btn--small" @click="goBack">← Back</button>
      <h1 class="instance-detail__title">{{ instance?.name || 'Loading...' }}</h1>
      <div class="instance-detail__actions">
        <button
          v-if="!launchState.running"
          class="btn"
          :disabled="!instance || launching || (launchStore.anyRunning && !launchState.running)"
          @click="handleLaunch"
        >
          {{ launching ? 'Preparing...' : 'Launch' }}
        </button>
        <button v-else class="btn btn--warning" @click="handleCancelLaunch">Stop Game</button>
        <button class="btn btn--danger btn--small" :disabled="!instance" @click="deleteConfirmOpen = true">
          Delete
        </button>
      </div>
    </header>

    <div v-if="error" class="detail-error">
      <span>{{ error }}</span>
      <button class="btn btn--ghost btn--small" @click="copyError">Copy</button>
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
        <h3 class="instance-detail__heading">Settings</h3>
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

      <section v-if="instance.mod_loader !== 'vanilla'" class="instance-detail__section">
        <div class="instance-detail__section-header">
          <h3 class="instance-detail__heading">Installed Mods</h3>
          <span class="instance-detail__badge">{{ mods.length }}</span>
        </div>

        <div v-if="modsLoading" class="pixel-loader">
          Loading Mods
          <span class="pixel-loader__dots">
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
          </span>
        </div>

        <div v-else-if="mods.length === 0" class="empty">
          <p class="empty__title">No mods installed</p>
          <p class="empty__hint">Browse the Modrinth library to add mods to this instance.</p>
          <RouterLink to="/modrinth" class="btn btn--small">Open Modrinth</RouterLink>
        </div>

        <ul v-else class="mod-list">
          <li v-for="mod in mods" :key="mod.id" class="mod-item">
            <div class="mod-item__icon">
              <img v-if="mod.icon_url" :src="mod.icon_url" :alt="mod.title" />
              <span v-else class="mod-item__placeholder">?</span>
            </div>
            <div class="mod-item__body">
              <span class="mod-item__title">{{ mod.title }}</span>
              <span class="mod-item__meta">
                {{ mod.version_name || 'latest' }} · {{ mod.project_type }}
              </span>
            </div>
            <button
              class="btn btn--danger btn--small"
              :disabled="removingModId === mod.id"
              @click="removeMod(mod.id)"
            >
              {{ removingModId === mod.id ? 'Removing...' : 'Remove' }}
            </button>
          </li>
        </ul>
      </section>

      <section v-if="launchState.progress" class="instance-detail__section">
        <h3 class="instance-detail__heading">Progress</h3>
        <div class="progress-bar">
          <div class="progress-bar__fill" :style="{ width: progressPercent + '%' }" />
        </div>
        <p class="text-secondary">{{ progressPercent }}% — {{ launchState.progress.message }}</p>
      </section>

      <section v-if="launchState.logs.length > 0" class="instance-detail__section">
        <div class="instance-detail__section-header">
          <h3 class="instance-detail__heading">Console</h3>
          <button class="btn btn--ghost btn--small" @click="copyConsole">Copy</button>
        </div>
        <div class="console">
          <div v-for="(line, i) in launchState.logs" :key="i" class="console__line">{{ line }}</div>
        </div>
      </section>

      <section v-if="launchState.exitCode !== null" class="instance-detail__section">
        <h3 class="instance-detail__heading">Game Exited</h3>
        <p class="text-secondary">Exit code: {{ launchState.exitCode }}</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  getInstance,
  getInstanceSettings,
  saveInstanceSettings,
  launchInstance,
  cancelLaunch,
  getGlobalSettings,
  deleteInstance,
  removeInstanceMod,
  listInstanceMods
} from '@/api'
import { useLaunchStore } from '@/stores/launch'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import type { Instance, LaunchSettings, InstanceMod } from '@/types'

const route = useRoute()
const router = useRouter()
const launchStore = useLaunchStore()
const authStore = useAuthStore()
const notifications = useNotificationsStore()

const instance = ref<Instance | null>(null)
const mods = ref<InstanceMod[]>([])
const modsLoading = ref(false)
const removingModId = ref<string | null>(null)
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

const progressPercent = computed(() => {
  if (!launchState.value.progress || launchState.value.progress.total === 0) return 0
  return Math.round(
    (launchState.value.progress.current / launchState.value.progress.total) * 100
  )
})

async function loadMods() {
  if (!instance.value || instance.value.mod_loader === 'vanilla') {
    mods.value = []
    return
  }
  modsLoading.value = true
  try {
    mods.value = await listInstanceMods(instance.value.id)
  } catch (e) {
    error.value = String(e)
  } finally {
    modsLoading.value = false
  }
}

async function removeMod(modId: string) {
  if (!instance.value) return
  removingModId.value = modId
  try {
    await removeInstanceMod(modId)
    mods.value = mods.value.filter((m) => m.id !== modId)
    notifications.notifySuccess('Mod removed', 'The mod was removed from this instance.')
  } catch (e) {
    error.value = String(e)
    notifications.notifyError('Failed to remove mod', String(e))
  } finally {
    removingModId.value = null
  }
}

onMounted(async () => {
  await launchStore.initListeners()
  try {
    const id = route.params.id as string
    instance.value = await getInstance(id)
    if (instance.value) {
      launchStore.ensureState(id)
      const loadedSettings = await getInstanceSettings(id)
      settings.value = { ...loadedSettings, auto_install_java: true }
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
      await loadMods()
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

watch(
  () => launchState.value.running,
  (isRunning) => {
    launching.value = isRunning
  }
)

function goBack() {
  router.push({ name: 'library' })
}

async function saveSettings() {
  if (!instance.value) return
  error.value = null
  saveMessage.value = null
  try {
    await saveInstanceSettings(instance.value.id, settings.value)
    saveMessage.value = 'Settings saved.'
    setTimeout(() => {
      saveMessage.value = null
    }, 2500)
  } catch (e) {
    error.value = String(e)
  }
}

async function handleDelete() {
  if (!instance.value) return
  error.value = null
  try {
    await deleteInstance(instance.value.id)
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
    notifications.notifyError('Launch failed', String(e))
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
  flex-wrap: wrap;
}

.instance-detail__title {
  flex: 1;
  font-size: 2rem;
  margin: 0;
}

.instance-detail__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
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
  margin: 0 0 1rem;
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

.instance-detail__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.instance-detail__heading {
  font-size: 1.1rem;
  margin: 0 0 1rem;
}

.instance-detail__section-header .instance-detail__heading {
  margin: 0;
}

.instance-detail__badge {
  font-family: var(--font-pixel);
  font-size: 0.65rem;
  padding: 0.25rem 0.5rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-group--checkbox {
  justify-content: flex-end;
}

.form-label {
  font-family: var(--font-pixel);
  font-size: 0.65rem;
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

.empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 1.5rem;
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
}

.empty__title {
  font-family: var(--font-pixel);
  font-size: 0.75rem;
  text-transform: uppercase;
  margin: 0;
}

.empty__hint {
  color: var(--text-secondary);
  margin: 0;
}

.mod-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.mod-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.mod-item__icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
}

.mod-item__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mod-item__placeholder {
  font-size: 1.2rem;
  color: var(--text-secondary);
}

.mod-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.mod-item__title {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mod-item__meta {
  font-size: 0.8rem;
  color: var(--text-secondary);
}
</style>