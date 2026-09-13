<template>
  <div class="modal-overlay" @click.self="handleClose">
    <div class="modal">
      <div class="modal__header">
        <h3 class="modal__title">Add to Instance</h3>
        <button class="modal__close" @click="handleClose">×</button>
      </div>
      <div class="modal__body">
        <p class="modal__intro">
          Add <strong>{{ project.title }}</strong> to one of your compatible instances.
        </p>

        <div v-if="loadingVersions" class="pixel-loader">
          Checking compatibility
          <span class="pixel-loader__dots">
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
          </span>
        </div>

        <div v-else-if="loadError" class="form-error form-error--block">{{ loadError }}</div>

        <div v-else-if="compatibleInstances.length === 0" class="empty">
          <p class="empty__title">No compatible instances</p>
          <p class="empty__hint">This mod version does not match any of your instances.</p>
        </div>

        <ul v-else class="instance-list">
          <li v-for="inst in compatibleInstances" :key="inst.id">
            <button
              class="instance-item"
              :disabled="installing"
              @click="$emit('select', inst.id)"
            >
              <span class="instance-item__name">{{ inst.name }}</span>
              <span class="instance-item__meta">{{ inst.minecraft_version }} · {{ inst.mod_loader }}</span>
            </button>
          </li>
        </ul>

        <div v-if="installing" class="install-status">
          <div class="pixel-loader">
            Adding Mod
            <span class="pixel-loader__dots">
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
            </span>
          </div>
        </div>

        <div v-if="error" class="form-error form-error--block">{{ error }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getModrinthVersions } from '@/api'
import { useInstanceStore } from '@/stores/instances'
import type { ModrinthSearchResult, ModrinthVersion } from '@/types'

const props = defineProps<{
  project: ModrinthSearchResult
  selectedVersion?: ModrinthVersion | null
  error?: string | null
  installing?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', instanceId: string): void
}>()

const instanceStore = useInstanceStore()
const versions = ref<ModrinthVersion[]>([])
const loadingVersions = ref(false)
const loadError = ref<string | null>(null)

const compatibleInstances = computed(() => {
  let supportedGameVersions: Set<string>
  let supportedLoaders: Set<string>

  if (props.selectedVersion) {
    supportedGameVersions = new Set(props.selectedVersion.game_versions)
    supportedLoaders = new Set(props.selectedVersion.loaders)
  } else if (versions.value.length > 0) {
    supportedGameVersions = new Set(versions.value.flatMap((v) => v.game_versions))
    supportedLoaders = new Set(versions.value.flatMap((v) => v.loaders))
  } else {
    return []
  }

  return instanceStore.instances.filter((instance) => {
    if (instance.mod_loader === 'vanilla') return false
    if (!supportedGameVersions.has(instance.minecraft_version)) return false
    const normalizedLoader = instance.mod_loader === 'modrinth' ? 'fabric' : instance.mod_loader
    return supportedLoaders.has(normalizedLoader)
  })
})

function handleClose() {
  if (props.installing) return
  emit('close')
}

onMounted(async () => {
  loadingVersions.value = true
  try {
    if (instanceStore.instances.length === 0) {
      await instanceStore.load()
    }
    if (!props.selectedVersion) {
      versions.value = await getModrinthVersions(props.project.project_id)
    }
  } catch (e) {
    loadError.value = String(e)
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
  max-width: 500px;
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
}

.modal__intro {
  margin: 0;
  color: var(--text-secondary);
}

.instance-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 45vh;
  overflow-y: auto;
}

.instance-item {
  width: 100%;
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  color: var(--text-primary);
  text-align: left;
  transition: border-color var(--transition-fast), background-color var(--transition-fast);
}

.instance-item:hover:not(:disabled) {
  border-color: var(--accent);
}

.instance-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.instance-item__name {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
}

.instance-item__meta {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.empty {
  padding: 1.5rem;
  text-align: center;
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
}

.empty__title {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  margin: 0 0 0.35rem;
}

.empty__hint {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin: 0;
}

.install-status {
  padding: 0.75rem 1rem;
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
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
</style>