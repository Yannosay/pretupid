<template>
  <div class="modal-overlay" @click.self="handleClose">
    <div class="modal">
      <div class="modal__header">
        <h3 class="modal__title">Create Modpack Instance</h3>
        <button class="modal__close" @click="handleClose">×</button>
      </div>
      <div class="modal__body">
        <p class="modal__intro">
          Create a new instance from <strong>{{ project.title }}</strong>.
          Installation runs in the background — you will see progress in the notification center.
        </p>

        <div class="form-group">
          <label class="form-label" for="modpack-name">Instance Name</label>
          <input
            id="modpack-name"
            v-model="name"
            type="text"
            class="input"
            :placeholder="project.title"
            :disabled="creating"
          />
        </div>

        <div class="form-group">
          <label class="form-label" for="modpack-version">Modpack Version</label>
          <div v-if="loadingVersions" class="pixel-loader">
            Loading Versions
            <span class="pixel-loader__dots">
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
            </span>
          </div>
          <select
            v-else
            id="modpack-version"
            v-model="selectedVersionId"
            class="input"
            :disabled="creating"
          >
            <option value="" disabled>Select a version</option>
            <option v-for="version in versions" :key="version.id" :value="version.id">
              {{ version.name }} — {{ version.game_versions[0] ?? 'unknown' }}
            </option>
          </select>
          <span v-if="versions.length > 0 && selectedVersion" class="form-hint">
            {{ selectedVersion.game_versions.join(', ') }} ·
            {{ selectedVersion.loaders.join(', ') }}
          </span>
        </div>

        <div v-if="error" class="form-error form-error--block">{{ error }}</div>

        <div class="modal__actions">
          <button class="btn btn--ghost" :disabled="creating" @click="handleClose">Cancel</button>
          <button class="btn" :disabled="!canSubmit || creating" @click="create">
            {{ creating ? 'Starting...' : 'Create Instance' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getModrinthVersions, createInstanceFromModpack } from '@/api'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import { useInstallsStore } from '@/stores/installs'
import type { ModrinthSearchResult, ModrinthVersion } from '@/types'

const props = defineProps<{
  project: ModrinthSearchResult
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created'): void
}>()

const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()
const installs = useInstallsStore()

const name = ref(props.project.title)
const versions = ref<ModrinthVersion[]>([])
const selectedVersionId = ref('')
const creating = ref(false)
const loadingVersions = ref(false)
const error = ref<string | null>(null)

const selectedVersion = computed(
  () => versions.value.find((v) => v.id === selectedVersionId.value) ?? null
)

const canSubmit = computed(
  () => name.value.trim().length > 0 && selectedVersionId.value.length > 0
)

async function create() {
  if (!canSubmit.value) return
  creating.value = true
  error.value = null
  try {
    const instance = await createInstanceFromModpack(
      props.project.project_id,
      selectedVersionId.value,
      name.value.trim()
    )
    if (instance && typeof instance.id === 'string') {
      installs.begin(instance.id, 'Preparing installation')
    }
    await instanceStore.load()
    emit('created')
    emit('close')
    notifications.notifyInfo(
      'Modpack install started',
      `Installing ${instance?.name ?? name.value.trim()} in the background.`,
      4000
    )
  } catch (e) {
    error.value = String(e)
    notifications.notifyError('Modpack install failed', String(e))
  } finally {
    creating.value = false
  }
}

function handleClose() {
  if (creating.value) return
  emit('close')
}

onMounted(async () => {
  loadingVersions.value = true
  try {
    versions.value = await getModrinthVersions(props.project.project_id)
    if (versions.value.length > 0) {
      selectedVersionId.value = versions.value[0].id
    }
  } catch (e) {
    error.value = String(e)
  } finally {
    loadingVersions.value = false
  }
})
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
}

.modal {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  width: 90%;
  max-width: 520px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 2px solid var(--border-color);
  background-color: var(--bg-tertiary);
}

.modal__title {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
  text-transform: uppercase;
  margin: 0;
}

.modal__close {
  font-size: 1.75rem;
  line-height: 1;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
}

.modal__close:hover {
  color: var(--text-primary);
}

.modal__body {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  overflow-y: auto;
}

.modal__intro {
  margin: 0;
  color: var(--text-secondary);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.form-label {
  font-family: var(--font-pixel);
  font-size: 0.65rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.form-hint {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.form-error {
  font-size: 0.9rem;
  color: #e74c3c;
}

.form-error--block {
  padding: 0.75rem 1rem;
  background-color: rgba(231, 76, 60, 0.12);
  border: 2px solid #e74c3c;
  border-radius: var(--edge-radius);
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}
</style>