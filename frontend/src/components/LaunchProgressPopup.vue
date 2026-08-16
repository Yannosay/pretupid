<template>
  <div v-if="activeState" class="launch-popup">
    <div class="launch-popup__header">
      <span class="launch-popup__title">{{ instanceName }} - {{ activeState.running ? 'Running' : 'Installing' }}</span>
    </div>
    <div v-if="activeState.progress" class="launch-popup__progress">
      <div class="progress-bar">
        <div class="progress-bar__fill" :style="{ width: progressPercent + '%' }" />
      </div>
      <span class="launch-popup__percent">{{ progressPercent }}%</span>
    </div>
    <p v-if="activeState.progress" class="launch-popup__message">{{ activeState.progress.message }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useLaunchStore } from '@/stores/launch'
import { useInstanceStore } from '@/stores/instances'

const launchStore = useLaunchStore()
const instanceStore = useInstanceStore()

const activeState = computed(() => launchStore.activeState)
const instanceName = computed(() => {
  if (!launchStore.activeInstanceId) return ''
  const inst = instanceStore.instances.find(i => i.id === launchStore.activeInstanceId)
  return inst ? inst.name : 'Instance'
})

const progressPercent = computed(() => {
  if (!activeState.value?.progress || activeState.value.progress.total === 0) return 0
  return Math.round((activeState.value.progress.current / activeState.value.progress.total) * 100)
})
</script>

<style scoped lang="scss">
.launch-popup {
  position: fixed;
  bottom: 1rem;
  right: 1rem;
  width: 320px;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1rem;
  z-index: 1500;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
}

.launch-popup__header {
  margin-bottom: 0.75rem;
}

.launch-popup__title {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  color: var(--accent);
  text-transform: uppercase;
}

.launch-popup__progress {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.progress-bar {
  flex: 1;
  height: 16px;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
}

.progress-bar__fill {
  height: 100%;
  background-color: var(--accent);
  transition: width var(--transition-normal);
}

.launch-popup__percent {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  color: var(--text-secondary);
  white-space: nowrap;
}

.launch-popup__message {
  margin-top: 0.5rem;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--text-secondary);
  word-break: break-all;
}
</style>