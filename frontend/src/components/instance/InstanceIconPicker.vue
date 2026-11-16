<template>
  <div class="panel icon-picker">
    <div class="icon-picker__head">
      <div
        class="icon-picker__preview"
        :class="{ 'icon-picker__preview--drop': dragging }"
        @dragover.prevent="dragging = true"
        @dragleave="dragging = false"
        @drop.prevent="onDrop"
      >
        <InstanceIcon :instance="instance" :size="72" />
      </div>
      <div class="icon-picker__intro">
        <h3 class="panel__title">Icon</h3>
        <p class="form-hint">Upload or drop your own picture, or pick any block from Minecraft.</p>
        <div class="icon-picker__actions">
          <button class="btn btn--secondary btn--small" :disabled="busy" @click="fileInput?.click()">
            <AppIcon name="upload" :size="12" />
            Upload image
          </button>
          <button v-if="instance.icon_url" class="btn btn--ghost btn--small" :disabled="busy" @click="apply({ icon_url: '' })">
            Remove image
          </button>
        </div>
        <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="sr-only" @change="onFile" />
      </div>
    </div>

    <div class="icon-picker__grid">
      <button
        v-for="option in featured"
        :key="option.style"
        class="icon-picker__swatch"
        :class="{ 'icon-picker__swatch--active': isActive(option.texture) }"
        :title="option.label"
        :aria-label="option.label"
        :disabled="busy"
        @click="choose(option.style)"
      >
        <InstanceIcon :instance="{ id: instance.id, name: option.label, icon_url: null, icon_style: option.style }" :size="40" />
      </button>
    </div>

    <div class="icon-picker__library">
      <div class="icon-picker__library-head">
        <label class="icon-picker__library-title" for="icon-picker-search">Every block in Minecraft</label>
        <span v-if="allTextures.length" class="icon-picker__library-count">{{ allTextures.length }} textures</span>
      </div>
      <input
        id="icon-picker-search"
        v-model="query"
        type="search"
        class="input"
        placeholder="Search blocks, for example diamond or cherry"
        autocomplete="off"
        spellcheck="false"
      />
      <p v-if="libraryState === 'loading'" class="form-hint">Reading block textures from your Minecraft files…</p>
      <p v-else-if="libraryState === 'empty'" class="form-hint">
        Block textures appear once a Minecraft version has been downloaded.
      </p>
      <template v-else-if="query.trim()">
        <div v-if="matches.length" class="icon-picker__grid icon-picker__grid--library">
          <button
            v-for="name in visibleMatches"
            :key="name"
            class="icon-picker__swatch"
            :class="{ 'icon-picker__swatch--active': isActive(name) }"
            :title="textureLabel(name)"
            :aria-label="textureLabel(name)"
            :disabled="busy"
            @click="choose(formatTextureStyle(name))"
          >
            <InstanceIcon :instance="{ id: instance.id, name, icon_url: null, icon_style: formatTextureStyle(name) }" :size="40" />
          </button>
        </div>
        <p v-else class="form-hint">No block matches “{{ query.trim() }}”.</p>
        <button v-if="matches.length > visibleMatches.length" class="btn btn--ghost btn--small icon-picker__more" @click="limit += PAGE">
          Show more ({{ matches.length - visibleMatches.length }})
        </button>
      </template>
    </div>

    <p v-if="instance.icon_url" class="form-hint">Your uploaded image is shown while it is set. Remove it to use a block.</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import {
  BLOCK_MATERIALS,
  formatIconStyle,
  formatTextureStyle,
  iconTextureName,
  imageFileToIcon,
  listAllBlockTextures,
  textureLabel
} from '@/utils/instanceIcon'
import type { Instance, UpdateInstanceRequest } from '@/types'

const PAGE = 72

const props = defineProps<{ instance: Instance }>()
const emit = defineEmits<{ (e: 'updated', instance: Instance): void }>()

const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()
const fileInput = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const dragging = ref(false)
const query = ref('')
const limit = ref(PAGE)
const allTextures = ref<string[]>([])
const libraryState = ref<'loading' | 'ready' | 'empty'>('loading')

const featured = Object.entries(BLOCK_MATERIALS).map(([value, material]) => ({
  style: formatIconStyle(value),
  texture: material.texture,
  label: material.label
}))

const currentTexture = computed(() => iconTextureName(props.instance.icon_style, props.instance.id))

const matches = computed(() => {
  const terms = query.value.trim().toLowerCase().split(/[\s_]+/).filter(Boolean)
  if (!terms.length) return []
  return allTextures.value.filter((name) => terms.every((term) => name.includes(term)))
})

const visibleMatches = computed(() => matches.value.slice(0, limit.value))

watch(query, () => {
  limit.value = PAGE
})

onMounted(async () => {
  allTextures.value = await listAllBlockTextures()
  libraryState.value = allTextures.value.length ? 'ready' : 'empty'
})

function isActive(texture: string): boolean {
  return !props.instance.icon_url && currentTexture.value === texture
}

async function apply(request: UpdateInstanceRequest) {
  busy.value = true
  try {
    const updated = await instanceStore.update(props.instance.id, request)
    if (updated) emit('updated', updated)
  } catch (e) {
    notifications.notifyError('Could not change the icon', String(e))
  } finally {
    busy.value = false
  }
}

function choose(style: string) {
  apply({ icon_style: style, icon_url: '' })
}

async function useImage(file: File | undefined) {
  if (!file) return
  try {
    const dataUrl = await imageFileToIcon(file)
    await apply({ icon_url: dataUrl })
  } catch (e) {
    notifications.notifyError('Could not use that image', e instanceof Error ? e.message : String(e))
  }
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  void useImage(file)
}

function onDrop(event: DragEvent) {
  dragging.value = false
  void useImage(event.dataTransfer?.files?.[0])
}
</script>

<style scoped lang="scss">
.icon-picker {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.icon-picker__head {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.icon-picker__preview {
  padding: 4px;
  border: 2px dashed transparent;
  border-radius: calc(var(--edge-radius) + 4px);
  transition: border-color var(--transition-fast), background-color var(--transition-fast);
}

.icon-picker__preview--drop {
  border-color: var(--accent);
  background-color: color-mix(in srgb, var(--accent) 12%, transparent);
}

.icon-picker__intro {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;

  .panel__title {
    margin: 0;
  }

  .form-hint {
    margin: 0;
  }
}

.icon-picker__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.25rem;
}

.icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(48px, 1fr));
  gap: 0.5rem;
}

.icon-picker__grid--library {
  max-height: 260px;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.icon-picker__library {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);

  .form-hint {
    margin: 0;
  }
}

.icon-picker__library-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.icon-picker__library-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.icon-picker__library-count {
  font-size: 0.8rem;
  color: var(--text-dim);
}

.icon-picker__more {
  align-self: flex-start;
}

.icon-picker__swatch {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  padding: 3px;
  background: none;
  border: 2px solid transparent;
  border-radius: calc(var(--edge-radius) + 2px);
  cursor: pointer;
  transition: border-color var(--transition-fast), transform var(--transition-fast);

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    border-color: var(--border-strong);
  }

  &:focus-visible {
    outline: none;
    border-color: var(--text-primary);
  }
}

.icon-picker__swatch--active {
  border-color: var(--accent);

  &:hover:not(:disabled) {
    border-color: var(--accent);
  }
}
</style>
