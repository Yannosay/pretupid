<template>
  <div class="library">
    <header class="library__header">
      <h1 class="library__title">Instances</h1>
      <button
        class="btn"
        :disabled="!authStore.authenticated || instanceStore.loading"
        @click="createModalOpen = true"
      >
        Create Instance
      </button>
    </header>

    <div v-if="!authStore.authenticated" class="library__empty">
      <p class="text-secondary">Login with Microsoft to manage instances.</p>
    </div>

    <div v-else-if="instanceStore.error" class="library__error">
      {{ instanceStore.error }}
    </div>

    <div v-else-if="instanceStore.loading" class="library__empty">
      <p class="text-secondary">Loading instances...</p>
    </div>

    <div v-else-if="instanceStore.instances.length === 0" class="library__empty">
      <p class="text-secondary">No instances yet. Create your first instance to get started.</p>
    </div>

    <div v-else class="library__grid">
      <div
        v-for="instance in instanceStore.instances"
        :key="instance.id"
        class="instance-card"
        :class="{ 'instance-card--disabled': launchStore.anyRunning && !isInstanceRunning(instance.id) }"
        @click="launchStore.anyRunning && !isInstanceRunning(instance.id) ? null : openInstance(instance.id)"
      >
        <div class="instance-card__header">
          <h3 class="instance-card__title">{{ instance.name }}</h3>
          <span class="instance-card__version">{{ instance.minecraft_version }}</span>
        </div>
        <p class="instance-card__description">{{ instance.description || 'No description' }}</p>
        <div class="instance-card__footer">
          <span class="instance-card__loader">{{ instance.mod_loader }}</span>
          <span v-if="isInstanceRunning(instance.id)" class="instance-card__running">Instance running</span>
        </div>
      </div>
    </div>

    <CreateInstanceModal
      v-if="createModalOpen"
      @close="createModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useInstanceStore } from '@/stores/instances'
import { useAuthStore } from '@/stores/auth'
import { useLaunchStore } from '@/stores/launch'
import CreateInstanceModal from '@/components/CreateInstanceModal.vue'
import type { CreateInstanceRequest } from '@/types'

const router = useRouter()
const instanceStore = useInstanceStore()
const authStore = useAuthStore()
const launchStore = useLaunchStore()
const createModalOpen = ref(false)

function isInstanceRunning(instanceId: string): boolean {
  return launchStore.getState(instanceId).running
}

onMounted(async () => {
  await launchStore.initListeners()
  await authStore.check()
  if (authStore.authenticated) {
    await instanceStore.load()
  }
})

function openInstance(id: string) {
  router.push({ name: 'instance-detail', params: { id } })
}

async function handleCreated(request: CreateInstanceRequest) {
  createModalOpen.value = false
  await instanceStore.create(request)
}
</script>

<style scoped lang="scss">
.library__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.library__title {
  font-size: 2rem;
}

.library__error {
  padding: 1rem;
  margin-bottom: 1.5rem;
  background-color: #e74c3c;
  color: white;
  border-radius: var(--edge-radius);
  border: 2px solid #c0392b;
}

.library__empty {
  padding: 3rem;
  text-align: center;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.library__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 1.5rem;
}

.instance-card {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.25rem;
  cursor: pointer;
  transition: transform var(--transition-fast),
              box-shadow var(--transition-fast),
              border-color var(--transition-fast);
}

.instance-card:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}

.instance-card--disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.instance-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.75rem;
}

.instance-card__title {
  font-size: 1rem;
  margin: 0;
}

.instance-card__version {
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--text-secondary);
  background-color: var(--bg-tertiary);
  padding: 0.2rem 0.5rem;
  border-radius: var(--edge-radius);
}

.instance-card__description {
  color: var(--text-secondary);
  font-size: 1rem;
}

.instance-card__footer {
  margin-top: 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.instance-card__loader {
  text-transform: uppercase;
  font-family: var(--font-pixel);
  font-size: 0.6rem;
  color: var(--accent);
}

.instance-card__running {
  font-family: var(--font-pixel);
  font-size: 0.6rem;
  color: #2ecc71;
  text-transform: uppercase;
}
</style>