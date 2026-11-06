<template>
  <Transition name="launch-popup">
    <div v-if="visible && activeState && launchStore.activeInstanceId" class="launch-popup" role="status">
      <div class="launch-popup__header">
        <span class="launch-popup__title">{{ instanceName }}</span>
        <button class="icon-btn icon-btn--small" title="Cancel" aria-label="Cancel" @click="cancel">
          <AppIcon name="x" :size="14" />
        </button>
      </div>
      <div class="progress-bar" :class="{ 'progress-bar--indeterminate': indeterminate }">
        <div class="progress-bar__fill" :style="indeterminate ? undefined : { width: progressPercent + '%' }" />
      </div>
      <p class="launch-popup__message">
        <span>{{ activeState.progress?.message }}</span>
        <span v-if="!indeterminate" class="launch-popup__percent">{{ progressPercent }}%</span>
      </p>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useLaunchStore } from '@/stores/launch'
import { useInstanceStore } from '@/stores/instances'

const launchStore = useLaunchStore()
const instanceStore = useInstanceStore()

const activeState = computed(() => launchStore.activeState)
const visible = computed(() => !!activeState.value?.progress && !activeState.value.running)
const instanceName = computed(() => {
  if (!launchStore.activeInstanceId) return ''
  return instanceStore.byId(launchStore.activeInstanceId)?.name ?? 'Instance'
})
const indeterminate = computed(() => !activeState.value?.progress || activeState.value.progress.total === 0)
const progressPercent = computed(() => {
  const p = activeState.value?.progress
  if (!p || p.total === 0) return 0
  return Math.min(100, Math.round((p.current / p.total) * 100))
})

function cancel() {
  if (launchStore.activeInstanceId) launchStore.stop(launchStore.activeInstanceId).catch(() => void 0)
}
</script>

<style scoped lang="scss">
.launch-popup {
  position: fixed;
  right: 1.25rem;
  bottom: 1.25rem;
  z-index: 1500;
  width: 340px;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem 1rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--accent);
  border-radius: var(--edge-radius);
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}

.launch-popup__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.launch-popup__title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 0.62rem;
  text-transform: uppercase;
  color: var(--accent);
}

.launch-popup__message {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0;
  font-size: 0.95rem;
  color: var(--text-secondary);
  word-break: break-word;
}

.launch-popup__percent {
  flex-shrink: 0;
  font-family: var(--font-pixel);
  font-size: 0.55rem;
}

.launch-popup-enter-active,
.launch-popup-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.launch-popup-enter-from,
.launch-popup-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
