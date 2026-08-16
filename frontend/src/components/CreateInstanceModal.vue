<template>
  <div v-if="modelValue" class="modal-overlay" @click.self="close">
    <div class="modal">
      <div class="modal__header">
        <h2 class="modal__title">Create Instance</h2>
        <button class="modal__close" @click="close">×</button>
      </div>

      <div class="modal__body">
        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label class="form-label">Name</label>
            <input v-model="form.name" class="input" required placeholder="My Minecraft Instance" />
          </div>

          <div class="form-group">
            <label class="form-label">Description</label>
            <textarea v-model="form.description" class="input" rows="2" placeholder="Optional" />
          </div>

          <div class="form-group">
            <label class="form-label">Minecraft Version</label>
            <select v-model="form.minecraft_version" class="input" required>
              <option value="" disabled>Select version</option>
              <option v-for="version in versions" :key="version.id" :value="version.id">
                {{ version.id }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">Mod Loader</label>
            <select v-model="form.mod_loader" class="input">
              <option value="vanilla">Vanilla</option>
              <option value="forge">Forge</option>
              <option value="fabric">Fabric</option>
              <option value="quilt">Quilt</option>
            </select>
          </div>

          <div v-if="error" class="form-error">{{ error }}</div>

          <button type="submit" class="btn" :disabled="creating || !form.name || !form.minecraft_version">
            {{ creating ? 'Creating...' : 'Create' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { fetchVersions, getGlobalSettings } from '@/api'
import type { VersionEntry } from '@/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created', instance: unknown): void
}>()

const versions = ref<VersionEntry[]>([])
const creating = ref(false)
const error = ref<string | null>(null)

const form = reactive({
  name: '',
  description: '',
  minecraft_version: '',
  mod_loader: 'vanilla' as const,
  loader_version: null as string | null
})

async function loadVersions() {
  const [loaded, gs] = await Promise.all([fetchVersions(), getGlobalSettings()])
  versions.value = gs.show_snapshots ? loaded : loaded.filter((v) => v.type === 'release')
}

onMounted(loadVersions)

watch(() => props.modelValue, (open) => {
  if (open) {
    loadVersions()
  }
})

async function handleSubmit() {
  creating.value = true
  error.value = null
  try {
    emit('created', { ...form })
    close()
  } catch (e) {
    error.value = String(e)
  } finally {
    creating.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
.form-group {
  margin-bottom: 1rem;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.form-error {
  padding: 0.75rem;
  margin-bottom: 1rem;
  background-color: #e74c3c;
  color: white;
  border-radius: var(--edge-radius);
  border: 2px solid #c0392b;
  font-family: var(--font-body);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  width: 90%;
  max-width: 520px;
  max-height: 80vh;
  overflow-y: auto;
  padding: 1.5rem;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.modal__title {
  font-size: 1.5rem;
}

.modal__close {
  font-size: 2rem;
  line-height: 1;
  color: var(--text-secondary);
  transition: color var(--transition-fast);
}

.modal__close:hover {
  color: var(--text-primary);
}

.modal__body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
</style>