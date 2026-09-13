<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal__header">
        <h3 class="modal__title">Import Skin</h3>
        <button class="modal__close" @click="$emit('close')">×</button>
      </div>

      <div class="modal__body">
        <div class="form-group">
          <label class="form-label">Source</label>
          <div class="source-tabs">
            <button
              class="btn btn--ghost btn--small"
              :class="{ 'btn--active': sourceMode === 'file' }"
              @click="sourceMode = 'file'"
            >
              File
            </button>
            <button
              class="btn btn--ghost btn--small"
              :class="{ 'btn--active': sourceMode === 'url' }"
              @click="sourceMode = 'url'"
            >
              URL
            </button>
          </div>
        </div>

        <div v-if="sourceMode === 'file'" class="form-group">
          <label class="form-label">Skin PNG</label>
          <input
            type="file"
            accept="image/png"
            class="input"
            @change="handleFileChange"
          />
          <span v-if="selectedFile" class="form-hint">{{ selectedFile.name }}</span>
        </div>

        <div v-else class="form-group">
          <label class="form-label">Skin URL</label>
          <input v-model="url" type="text" class="input" placeholder="https://example.com/skin.png" />
        </div>

        <div class="form-group">
          <label class="form-label">Slot Name</label>
          <input v-model="name" type="text" class="input" placeholder="My Skin" />
        </div>

        <div class="form-group">
          <label class="form-label">Model Type</label>
          <select v-model="modelType" class="input">
            <option value="classic">Classic</option>
            <option value="slim">Slim</option>
          </select>
        </div>

        <div v-if="importing" class="pixel-loader">
          Importing
          <span class="pixel-loader__dots">
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
          </span>
        </div>
        <div v-else-if="error || storeError" class="error">{{ error || storeError }}</div>

        <div class="modal__actions">
          <button class="btn" :disabled="!canSubmit || importing" @click="submit">Import</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SkinModelType } from '@/types/profile'

const props = defineProps<{
  importing: boolean
  error?: string | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'import-file', payload: { file: File; name: string; modelType: SkinModelType }): void
  (e: 'import-url', payload: { url: string; name: string; modelType: SkinModelType }): void
}>()

const selectedFile = ref<File | null>(null)
const sourceMode = ref<'file' | 'url'>('file')
const url = ref('')
const name = ref('')
const modelType = ref<SkinModelType>('classic')
const importing = computed(() => props.importing)
const storeError = computed(() => props.error ?? null)

const canSubmit = computed(() => {
  if (name.value.trim().length === 0) return false

  if (sourceMode.value === 'file') {
    return selectedFile.value !== null
  }

  return url.value.trim().length > 0
})

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  selectedFile.value = file

  if (file && name.value.trim().length === 0) {
    name.value = file.name.replace(/\.png$/i, '')
  }
}

function submit() {
  if (!canSubmit.value) return

  if (sourceMode.value === 'file' && selectedFile.value) {
    emit('import-file', {
      file: selectedFile.value,
      name: name.value.trim(),
      modelType: modelType.value
    })
  } else if (sourceMode.value === 'url') {
    emit('import-url', {
      url: url.value.trim(),
      name: name.value.trim(),
      modelType: modelType.value
    })
  }
}
</script>

<style scoped lang="scss">
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
  z-index: 4000;
}

.modal {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  width: 90%;
  max-width: 520px;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.modal__close {
  font-size: 2rem;
  line-height: 1;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.form-label {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.form-hint {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.source-tabs {
  display: flex;
  gap: 0.5rem;
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}

.error {
  padding: 1rem;
  background-color: #e74c3c;
  color: white;
  border-radius: var(--edge-radius);
  border: 2px solid #c0392b;
}
</style>