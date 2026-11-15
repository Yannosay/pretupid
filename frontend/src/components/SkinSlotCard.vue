<template>
  <div
    class="skin-slot-card"
    :class="{ 'skin-slot-card--active': active }"
    draggable="true"
    @dragstart="$emit('drag-start', slot)"
    @dragover.prevent
    @drop="$emit('drop', slot)"
    @click="$emit('select')"
  >
    <div class="skin-slot-card__preview">
      <img v-if="slot.data_url || previewUrl" :src="slot.data_url || previewUrl" :alt="slot.name" />
      <span v-else class="skin-slot-card__placeholder">?</span>
    </div>

    <div class="skin-slot-card__body">
      <div class="skin-slot-card__header">
        <span class="skin-slot-card__name">{{ slot.name }}</span>
        <span v-if="active" class="skin-slot-card__active">ACTIVE</span>
      </div>

      <p class="skin-slot-card__meta">
        {{ slot.model_type }} · {{ slot.source.replace('_', ' ') }}
      </p>

      <div class="skin-slot-card__actions">
        <button
          class="btn btn--ghost btn--small"
          :disabled="active"
          @click.stop="$emit('select')"
        >
          Select
        </button>
        <button class="btn btn--ghost btn--small" @click.stop="$emit('export')">
          Export
        </button>
        <button class="btn btn--ghost btn--small btn--danger" @click.stop="$emit('delete')">
          Remove
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SkinSlotDto } from '@/types/profile'

defineProps<{
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
</script>

<style scoped lang="scss">
.skin-slot-card {
  display: flex;
  gap: 1rem;
  padding: 0.75rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: border-color var(--transition-fast), transform var(--transition-fast);
}

.skin-slot-card:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
}

.skin-slot-card--active {
  border-color: var(--accent);
  box-shadow: 3px 3px 0 0 var(--shadow-color);
}

.skin-slot-card__preview {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
}

.skin-slot-card__preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.skin-slot-card__placeholder {
  font-size: 1.5rem;
  color: var(--text-secondary);
}

.skin-slot-card__body {
  flex: 1;
  min-width: 0;
}

.skin-slot-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.skin-slot-card__name {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-slot-card__active {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--accent-text);
  background-color: var(--accent);
  padding: 0.2rem 0.4rem;
  border-radius: var(--edge-radius);
}

.skin-slot-card__meta {
  margin: 0.4rem 0 0.6rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.skin-slot-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.btn--danger {
  color: #fff;
  border-color: var(--danger);
  background-color: var(--danger);
}
</style>