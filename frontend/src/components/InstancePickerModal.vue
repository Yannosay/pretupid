<template>
  <div class="modal-overlay" v-backdrop-close="() => emit('close')">
    <div class="modal" role="dialog" aria-modal="true">
      <div class="modal__header">
        <div>
          <h3 class="modal__title">Install {{ title }}</h3>
          <p class="modal__subtitle">Choose the instance to add it to.</p>
        </div>
        <button class="modal__close" aria-label="Close" @click="emit('close')">×</button>
      </div>
      <div class="modal__body">
        <div v-if="instanceStore.instances.length === 0" class="empty empty--centered">
          <p class="empty__title">No instances yet</p>
          <p class="empty__hint">Create an instance in your Library first.</p>
        </div>
        <ul v-else class="list picker__list">
          <li v-for="entry in ranked" :key="entry.instance.id">
            <button
              class="list__item picker__item"
              :disabled="!entry.compatible || busy"
              :title="entry.reason ?? ''"
              @click="emit('pick', entry.instance)"
            >
              <InstanceIcon :instance="entry.instance" :size="40" />
              <span class="list__body">
                <span class="picker__name">{{ entry.instance.name }}</span>
                <span class="list__meta">
                  {{ entry.instance.minecraft_version }} · {{ loaderLabel(entry.instance.mod_loader) }}
                  <template v-if="entry.reason"> · {{ entry.reason }}</template>
                </span>
              </span>
              <AppIcon v-if="entry.compatible" name="plus" :size="16" />
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { vBackdropClose } from '@ui/backdropClose'
import { useEscapeKey } from '@/composables/useEscapeKey'
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import { useInstanceStore } from '@/stores/instances'
import { loaderLabel } from '@/utils/format'
import { compatibility } from '@/utils/compat'
import type { Instance } from '@/types'

const props = defineProps<{
  title: string
  projectType: string
  gameVersions: string[]
  loaders: string[]
  busy?: boolean
}>()

const emit = defineEmits<{ (e: 'close'): void; (e: 'pick', instance: Instance): void }>()

const instanceStore = useInstanceStore()

const ranked = computed(() =>
  instanceStore.recent
    .map((instance) => {
      const reason = compatibility(instance, props.projectType, props.gameVersions, props.loaders)
      return { instance, compatible: reason === null, reason }
    })
    .sort((a, b) => Number(b.compatible) - Number(a.compatible))
)
useEscapeKey(() => true, () => emit('close'))
</script>

<style scoped lang="scss">
.picker__list {
  max-height: 55vh;
  overflow-y: auto;
}

.picker__item {
  width: 100%;
  text-align: left;
  color: var(--text-primary);
  cursor: pointer;

  &:hover:not(:disabled) {
    border-color: var(--accent);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.picker__name {
  font-size: 1.1rem;
}
</style>
