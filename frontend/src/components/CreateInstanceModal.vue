<template>
  <div class="modal-overlay" @click.self="handleClose">
    <div class="modal">
      <div class="modal__header">
        <h3 class="modal__title">Create Instance</h3>
        <button class="modal__close" @click="handleClose">×</button>
      </div>

      <div class="modal__body">
        <div class="form-group">
          <label class="form-label" for="instance-name">Instance Name</label>
          <input
            id="instance-name"
            v-model="name"
            type="text"
            class="input"
            placeholder="My Survival World"
            :disabled="creating"
            autofocus
          />
        </div>

        <div class="form-group">
          <label class="form-label" for="instance-version">Minecraft Version</label>
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
            id="instance-version"
            v-model="minecraftVersion"
            class="input"
            :disabled="creating"
          >
            <option value="" disabled>Select a version</option>
            <option v-for="version in filteredVersions" :key="version.id" :value="version.id">
              {{ version.id }} · {{ version.type }}
            </option>
          </select>
          <span v-if="versionsError" class="form-error">{{ versionsError }}</span>
        </div>

        <div class="form-group">
          <label class="form-label" for="instance-loader">Mod Loader</label>
          <select
            id="instance-loader"
            v-model="loader"
            class="input"
            :disabled="creating"
          >
            <option value="vanilla">Vanilla — no mods</option>
            <option value="fabric">Fabric</option>
            <option value="quilt">Quilt</option>
            <option value="forge">Forge</option>
            <option value="neoforge">NeoForge</option>
          </select>
          <span class="form-hint">
            {{ loaderHint }}
          </span>
        </div>

        <div v-if="loader !== 'vanilla'" class="form-group">
          <label class="form-label" for="instance-loader-version">Loader Version</label>
          <input
            id="instance-loader-version"
            v-model="loaderVersion"
            type="text"
            class="input"
            :placeholder="loaderVersionPlaceholder"
            :disabled="creating"
          />
          <span class="form-hint">{{ loaderVersionHint }}</span>
        </div>

        <div class="form-group">
          <label class="form-label" for="instance-description">Description <span class="form-label-optional">(optional)</span></label>
          <input
            id="instance-description"
            v-model="description"
            type="text"
            class="input"
            placeholder="Notes for this instance"
            :disabled="creating"
          />
        </div>

        <div v-if="submitError" class="form-error form-error--block">{{ submitError }}</div>

        <div class="modal__actions">
          <button class="btn btn--ghost" :disabled="creating" @click="handleClose">Cancel</button>
          <button class="btn" :disabled="!canSubmit || creating" @click="submit">
            {{ creating ? 'Creating...' : 'Create Instance' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { fetchVersions } from '@/api'
import { useInstanceStore } from '@/stores/instances'
import { useSettingsStore } from '@/stores/settings'
import { useNotificationsStore } from '@/stores/notifications'
import type { VersionEntry } from '@/types'

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created'): void
}>()

const instanceStore = useInstanceStore()
const settingsStore = useSettingsStore()
const notifications = useNotificationsStore()

const versions = ref<VersionEntry[]>([])
const loadingVersions = ref(false)
const versionsError = ref<string | null>(null)

const name = ref('')
const description = ref('')
const minecraftVersion = ref('')
const loader = ref<'vanilla' | 'fabric' | 'quilt' | 'forge' | 'neoforge'>('vanilla')
const loaderVersion = ref('')

const creating = ref(false)
const submitError = ref<string | null>(null)

const filteredVersions = computed(() => {
  if (!versions.value.length) return []
  if (settingsStore.settings.globalSettings.show_snapshots) return versions.value
  const releases = versions.value.filter((v) => v.type === 'release')
  return releases.length > 0 ? releases : versions.value
})

const loaderHint = computed(() => {
  switch (loader.value) {
    case 'vanilla':
      return 'Pure Minecraft. Mods are not supported on this instance.'
    case 'fabric':
      return 'Lightweight, modern mod loader. Most Modrinth mods target Fabric.'
    case 'quilt':
      return 'Fabric-compatible, community-driven fork.'
    case 'forge':
      return 'Classic mod loader. Heavy modpacks still target Forge.'
    case 'neoforge':
      return 'Modern fork of Forge for Minecraft 1.20.2+.'
    default:
      return ''
  }
})

const loaderVersionPlaceholder = computed(() => {
  switch (loader.value) {
    case 'fabric':
      return 'latest'
    case 'quilt':
      return 'latest'
    case 'forge':
      return 'e.g. 47.2.0'
    case 'neoforge':
      return 'e.g. 20.4.237'
    default:
      return ''
  }
})

const loaderVersionHint = computed(() => {
  switch (loader.value) {
    case 'fabric':
    case 'quilt':
      return 'Leave blank to use the latest stable loader version.'
    case 'forge':
    case 'neoforge':
      return 'Required. Provide the exact loader version to install.'
    default:
      return ''
  }
})

const canSubmit = computed(() => {
  if (creating.value) return false
  if (!name.value.trim()) return false
  if (!minecraftVersion.value) return false
  if (loader.value === 'forge' || loader.value === 'neoforge') {
    return loaderVersion.value.trim().length > 0
  }
  return true
})

async function submit() {
  if (!canSubmit.value) return
  submitError.value = null
  creating.value = true

  try {
    const trimmedLoaderVersion = loaderVersion.value.trim()

    await instanceStore.create({
      name: name.value.trim(),
      description: description.value.trim(),
      minecraft_version: minecraftVersion.value,
      mod_loader: loader.value,
      loader_version: loader.value === 'vanilla'
        ? null
        : trimmedLoaderVersion.length > 0
          ? trimmedLoaderVersion
          : null
    })

    notifications.notifySuccess(
      'Instance created',
      `${name.value.trim()} is ready to configure.`
    )
    emit('created')
    emit('close')
  } catch (e) {
    submitError.value = String(e)
    notifications.notifyError('Instance creation failed', String(e))
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
  versionsError.value = null
  try {
    versions.value = await fetchVersions()
    if (versions.value.length > 0) {
      const releases = versions.value.filter((v) => v.type === 'release')
      minecraftVersion.value = releases[0]?.id ?? versions.value[0].id
    }
  } catch (e) {
    versionsError.value = String(e)
    notifications.notifyError('Could not load versions', String(e))
  } finally {
    loadingVersions.value = false
  }
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

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
  transition: color var(--transition-fast);
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

.form-label-optional {
  font-size: 0.6rem;
  color: var(--text-secondary);
  opacity: 0.75;
}

.form-hint {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.form-error {
  font-size: 0.85rem;
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