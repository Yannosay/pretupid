<template>
  <div
    class="skin-slot-card"
    :class="{ 'skin-slot-card--active': active }"
    draggable="true"
    role="button"
    tabindex="0"
    :aria-pressed="active"
    @dragstart="$emit('drag-start', slot)"
    @dragover.prevent
    @drop="$emit('drop', slot)"
    @click="$emit('select')"
    @keydown.enter.prevent="$emit('select')"
    @keydown.space.prevent="$emit('select')"
  >
    <div class="skin-slot-card__preview">
      <template v-if="texture">
        <span class="skin-slot-card__face" :style="{ backgroundImage: `url(${texture})` }" />
        <span class="skin-slot-card__face skin-slot-card__face--hat" :style="{ backgroundImage: `url(${texture})` }" />
      </template>
      <span v-else class="skin-slot-card__placeholder">?</span>
      <span v-if="active" class="skin-slot-card__badge">Active</span>
    </div>

    <div class="skin-slot-card__body">
      <span class="skin-slot-card__name" :title="slot.name">{{ slot.name }}</span>
      <span class="skin-slot-card__meta">{{ slot.model_type === 'slim' ? 'Slim arms' : 'Classic arms' }}</span>
    </div>

    <div class="skin-slot-card__actions">
      <button class="icon-btn icon-btn--small" title="Export PNG" aria-label="Export PNG" @click.stop="$emit('export')">
        <AppIcon name="download" :size="14" />
      </button>
      <button class="icon-btn icon-btn--small skin-slot-card__remove" title="Remove" aria-label="Remove skin" @click.stop="$emit('delete')">
        <AppIcon name="trash" :size="14" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import type { SkinSlotDto } from '@/types/profile'

const props = defineProps<{
  slot: SkinSlotDto
  active: boolean
  previewUrl: string
}>()

defineEmits<{
  (e: 'select'): void
  (e: 'export'): void
  (e: 'delete'): void
  (e: 'drag-start', slot: SkinSlotDto): void
  (e: 'drop', slot: SkinSlotDto): void
}>()

const texture = computed(() => props.slot.data_url || props.previewUrl)
</script>

<style scoped lang="scss">
.skin-slot-card {
  --face: 72px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 1rem 0.75rem 0.75rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: border-color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);

  &:hover {
    border-color: var(--accent);
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--text-primary);
    outline-offset: 2px;
  }

  &:hover .skin-slot-card__face--hat {
    transform: scale(1.14);
  }
}

.skin-slot-card--active {
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent), 0 0 20px color-mix(in srgb, var(--accent) 28%, transparent);
}

.skin-slot-card__preview {
  position: relative;
  width: calc(var(--face) + 16px);
  height: calc(var(--face) + 16px);
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--well-bg);
  border: 1px solid var(--border-color);
}

.skin-slot-card__face {
  position: absolute;
  width: var(--face);
  height: var(--face);
  background-repeat: no-repeat;
  background-size: calc(var(--face) * 8) auto;
  background-position: calc(var(--face) * -1) calc(var(--face) * -1);
  image-rendering: pixelated;
  transition: transform var(--transition-fast);
}

.skin-slot-card__face--hat {
  background-position: calc(var(--face) * -5) calc(var(--face) * -1);
  transform: scale(1.1);
}

.skin-slot-card__placeholder {
  font-size: 1.5rem;
  color: var(--text-secondary);
}

.skin-slot-card__badge {
  position: absolute;
  bottom: -9px;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.15rem 0.4rem;
  font-family: var(--font-pixel);
  font-size: 0.6rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--accent-text);
  background-color: var(--accent);
  white-space: nowrap;
}

.skin-slot-card__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  width: 100%;
  min-width: 0;
  margin-top: 0.25rem;
}

.skin-slot-card__name {
  max-width: 100%;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-slot-card__meta {
  font-size: 0.78rem;
  color: var(--text-secondary);
}

.skin-slot-card__actions {
  display: flex;
  gap: 0.35rem;
}

.skin-slot-card__remove:hover {
  color: var(--danger);
  border-color: var(--danger);
}
</style>
