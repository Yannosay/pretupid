<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal skin-import" role="dialog" aria-modal="true" aria-labelledby="skin-import-title">
      <div class="modal__header">
        <h3 id="skin-import-title" class="modal__title">Add a skin</h3>
        <button class="icon-btn icon-btn--small" aria-label="Close" @click="$emit('close')">
          <AppIcon name="x" :size="16" />
        </button>
      </div>

      <div class="modal__body skin-import__body">
        <div class="segmented skin-import__tabs" role="tablist" aria-label="Skin source">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="segmented__option"
            :class="{ 'segmented__option--active': sourceMode === tab.id }"
            role="tab"
            :aria-selected="sourceMode === tab.id"
            @click="sourceMode = tab.id"
          >
            <AppIcon :name="tab.icon" :size="12" />
            {{ tab.label }}
          </button>
        </div>

        <div class="skin-import__layout">
          <div class="skin-import__preview" :class="{ 'skin-import__preview--empty': !previewSrc }">
            <img v-if="previewSrc" :src="previewSrc" alt="" @error="previewBroken = true" />
            <AppIcon v-else name="user" :size="34" />
          </div>

          <div class="skin-import__fields">
            <div v-if="sourceMode === 'username'" class="form-group">
              <label class="form-label" for="skin-username">Minecraft username</label>
              <input
                id="skin-username"
                v-model="username"
                type="text"
                class="input"
                placeholder="e.g. Dream"
                maxlength="16"
                autocomplete="off"
                spellcheck="false"
                @keydown.enter="submit"
              />
              <span class="form-hint" :class="{ 'skin-import__warn': lookupError }">
                {{ lookupError ?? (lookingUp ? 'Looking up player' : lookupModel ? `Found a ${lookupModel === 'slim' ? 'slim' : 'classic'} skin` : 'Copies the skin that player is wearing right now.') }}
              </span>
            </div>

            <div v-else-if="sourceMode === 'file'" class="form-group">
              <span class="form-label">Skin file</span>
              <button class="skin-import__drop" @click="fileInput?.click()">
                <AppIcon name="folder" :size="18" />
                <span>{{ selectedFile ? selectedFile.name : 'Choose a PNG from your computer' }}</span>
              </button>
              <input ref="fileInput" type="file" accept="image/png" class="sr-only" @change="handleFileChange" />
              <span v-if="fileError" class="form-hint skin-import__warn">{{ fileError }}</span>
            </div>

            <div v-else class="form-group">
              <label class="form-label" for="skin-url">Image link</label>
              <input id="skin-url" v-model="url" type="url" class="input" placeholder="https://example.com/skin.png" @keydown.enter="submit" />
            </div>

            <div class="form-group">
              <label class="form-label" for="skin-name">Name</label>
              <input id="skin-name" v-model="name" type="text" class="input" maxlength="32" :placeholder="namePlaceholder" />
            </div>

            <div v-if="sourceMode !== 'username'" class="form-group">
              <span class="form-label">Arms</span>
              <div class="segmented">
                <button class="segmented__option" :class="{ 'segmented__option--active': modelType === 'classic' }" @click="modelType = 'classic'">
                  Classic (4px)
                </button>
                <button class="segmented__option" :class="{ 'segmented__option--active': modelType === 'slim' }" @click="modelType = 'slim'">
                  Slim (3px)
                </button>
              </div>
              <span v-if="autoDetected" class="form-hint">Detected automatically from the image.</span>
            </div>
          </div>
        </div>

        <div v-if="error || storeError" class="form-error form-error--block">{{ error || storeError }}</div>
      </div>

      <div class="modal__footer">
        <button class="btn btn--ghost" @click="$emit('close')">Cancel</button>
        <button class="btn" :disabled="!canSubmit || importing" @click="submit">
          {{ importing ? 'Adding' : 'Add skin' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import { lookupPlayerSkin } from '@/api/profile'
import type { SkinModelType } from '@/types/profile'

type SourceMode = 'username' | 'file' | 'url'

const props = defineProps<{
  importing: boolean
  error?: string | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'import-file', payload: { file: File; name: string; modelType: SkinModelType }): void
  (e: 'import-url', payload: { url: string; name: string; modelType: SkinModelType }): void
  (e: 'import-username', payload: { username: string; name: string }): void
}>()

const USERNAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/

const tabs: { id: SourceMode; label: string; icon: IconName }[] = [
  { id: 'username', label: 'Username', icon: 'user' },
  { id: 'file', label: 'File', icon: 'folder' },
  { id: 'url', label: 'Link', icon: 'external' }
]

const sourceMode = ref<SourceMode>('username')
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const filePreview = ref<string | null>(null)
const fileError = ref<string | null>(null)
const url = ref('')
const username = ref('')
const name = ref('')
const modelType = ref<SkinModelType>('classic')
const autoDetected = ref(false)
const lookingUp = ref(false)
const lookupError = ref<string | null>(null)
const lookupModel = ref<SkinModelType | null>(null)
const previewBroken = ref(false)
let lookupTimer: number | null = null
let lookupGeneration = 0

const importing = computed(() => props.importing)
const storeError = computed(() => props.error ?? null)
const error = ref<string | null>(null)

const trimmedUsername = computed(() => username.value.trim())

const namePlaceholder = computed(() => {
  if (sourceMode.value === 'username') return trimmedUsername.value || 'Uses the username'
  if (sourceMode.value === 'file') return selectedFile.value?.name.replace(/\.png$/i, '') || 'My skin'
  return 'My skin'
})

const previewSrc = computed(() => {
  if (previewBroken.value) return null
  if (sourceMode.value === 'username') {
    return USERNAME_PATTERN.test(trimmedUsername.value) && lookupModel.value
      ? `https://mc-heads.net/body/${encodeURIComponent(trimmedUsername.value)}/120`
      : null
  }
  if (sourceMode.value === 'file') return filePreview.value
  return null
})

const canSubmit = computed(() => {
  if (sourceMode.value === 'username') return USERNAME_PATTERN.test(trimmedUsername.value) && !lookupError.value && !lookingUp.value
  if (sourceMode.value === 'file') return selectedFile.value !== null && !fileError.value
  return /^https:\/\/\S+$/.test(url.value.trim())
})

function resolvedName(): string {
  const typed = name.value.trim()
  if (typed) return typed
  if (sourceMode.value === 'username') return trimmedUsername.value
  if (sourceMode.value === 'file' && selectedFile.value) return selectedFile.value.name.replace(/\.png$/i, '').slice(0, 32)
  return 'My skin'
}

watch([sourceMode, trimmedUsername], () => {
  previewBroken.value = false
  error.value = null
})

watch(trimmedUsername, (value) => {
  if (lookupTimer !== null) window.clearTimeout(lookupTimer)
  lookupError.value = null
  lookupModel.value = null
  lookingUp.value = false
  if (!value) return
  if (!USERNAME_PATTERN.test(value)) {
    lookupError.value = 'Usernames are 1 to 16 letters, numbers or underscores.'
    return
  }
  lookingUp.value = true
  const generation = ++lookupGeneration
  lookupTimer = window.setTimeout(async () => {
    try {
      const found = await lookupPlayerSkin(value)
      if (generation !== lookupGeneration) return
      lookupModel.value = found.model_type
    } catch (e) {
      if (generation !== lookupGeneration) return
      lookupError.value = String(e)
    } finally {
      if (generation === lookupGeneration) lookingUp.value = false
    }
  }, 450)
})

function detectSlim(image: HTMLImageElement): boolean | null {
  if (image.naturalWidth !== 64 || image.naturalHeight !== 64) return image.naturalHeight === 32 ? false : null
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 64
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null
  ctx.drawImage(image, 0, 0)
  const alpha = ctx.getImageData(54, 20, 2, 12).data
  for (let i = 3; i < alpha.length; i += 4) {
    if (alpha[i] !== 0) return false
  }
  return true
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  fileError.value = null
  autoDetected.value = false
  if (filePreview.value) URL.revokeObjectURL(filePreview.value)
  filePreview.value = null
  selectedFile.value = file
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    fileError.value = 'That file is too large to be a skin.'
    return
  }
  const objectUrl = URL.createObjectURL(file)
  filePreview.value = objectUrl
  try {
    const image = new Image()
    image.src = objectUrl
    await image.decode()
    if (image.naturalWidth !== 64 || (image.naturalHeight !== 64 && image.naturalHeight !== 32)) {
      fileError.value = 'Skins must be 64×64 or 64×32 pixels.'
      return
    }
    const slim = detectSlim(image)
    if (slim !== null) {
      modelType.value = slim ? 'slim' : 'classic'
      autoDetected.value = true
    }
  } catch {
    fileError.value = 'That file is not a readable PNG image.'
  }
}

function submit() {
  if (!canSubmit.value || importing.value) return
  error.value = null
  const finalName = resolvedName()
  if (sourceMode.value === 'username') {
    emit('import-username', { username: trimmedUsername.value, name: finalName })
  } else if (sourceMode.value === 'file' && selectedFile.value) {
    emit('import-file', { file: selectedFile.value, name: finalName, modelType: modelType.value })
  } else if (sourceMode.value === 'url') {
    emit('import-url', { url: url.value.trim(), name: finalName, modelType: modelType.value })
  }
}

onBeforeUnmount(() => {
  if (lookupTimer !== null) window.clearTimeout(lookupTimer)
  if (filePreview.value) URL.revokeObjectURL(filePreview.value)
})
</script>

<style scoped lang="scss">
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background-color: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
}

.skin-import {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 560px;
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-strong);
  border-radius: var(--edge-radius);
  box-shadow: 0 24px 60px var(--shadow-color);
}

.modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.1rem 1.25rem 0.25rem;
}

.skin-import__body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem 1.25rem;
}

.skin-import__tabs {
  display: flex;
  flex-wrap: nowrap;

  .segmented__option {
    flex: 1;
    justify-content: center;
  }
}

.skin-import__layout {
  display: flex;
  gap: 1.25rem;
}

.skin-import__preview {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 120px;
  height: 200px;
  color: var(--text-dim);
  background:
    radial-gradient(circle at 50% 80%, color-mix(in srgb, var(--accent) 18%, transparent), transparent 70%),
    var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);

  img {
    max-width: 100%;
    max-height: 100%;
    image-rendering: pixelated;
  }
}

.skin-import__fields {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.9rem;
  min-width: 0;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.skin-import__drop {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.85rem;
  font: inherit;
  text-align: left;
  color: var(--text-secondary);
  background-color: var(--bg-tertiary);
  border: 1px dashed var(--border-strong);
  border-radius: var(--edge-radius);
  cursor: pointer;

  span {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &:hover {
    color: var(--text-primary);
    border-color: var(--accent);
  }
}

.skin-import__warn {
  color: var(--warning);
}

.modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem 1.1rem;
  border-top: 1px solid var(--border-color);
}

@media (max-width: 520px) {
  .skin-import__preview {
    display: none;
  }
}
</style>
