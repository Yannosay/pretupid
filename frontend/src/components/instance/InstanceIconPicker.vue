<template>
  <div class="panel icon-picker">
    <div class="icon-picker__head">
      <InstanceIcon :instance="instance" :size="72" />
      <div class="icon-picker__intro">
        <h3 class="panel__title">Icon</h3>
        <p class="form-hint">Upload your own picture or pick a block.</p>
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
        v-for="option in options"
        :key="option.value"
        class="icon-picker__swatch"
        :class="{ 'icon-picker__swatch--active': isActive(option.value) }"
        :title="option.label"
        :aria-label="option.label"
        :disabled="busy"
        @click="choose(option.value)"
      >
        <InstanceIcon :instance="{ id: instance.id, name: option.label, icon_url: null, icon_style: formatIconStyle(option.value) }" :size="40" />
      </button>
    </div>
    <p v-if="instance.icon_url" class="form-hint">Your uploaded image is shown while it is set. Remove it to use a block.</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import { BLOCK_MATERIALS, autoMaterial, formatIconStyle, imageFileToIcon, parseIconStyle } from '@/utils/instanceIcon'
import type { Instance, UpdateInstanceRequest } from '@/types'

const props = defineProps<{ instance: Instance }>()
const emit = defineEmits<{ (e: 'updated', instance: Instance): void }>()

const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()
const fileInput = ref<HTMLInputElement | null>(null)
const busy = ref(false)

const current = computed(() => parseIconStyle(props.instance.icon_style) ?? autoMaterial(props.instance.id))

const options = Object.entries(BLOCK_MATERIALS).map(([value, m]) => ({ value, label: m.label }))

function isActive(value: string): boolean {
  return !props.instance.icon_url && current.value === value
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

function choose(value: string) {
  apply({ icon_style: formatIconStyle(value), icon_url: '' })
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const dataUrl = await imageFileToIcon(file)
    await apply({ icon_url: dataUrl })
  } catch (e) {
    notifications.notifyError('Could not use that image', e instanceof Error ? e.message : String(e))
  }
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
