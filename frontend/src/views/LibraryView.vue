<template>
  <div class="library">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">Library</h1>
        <p class="page-header__subtitle">
          {{ instanceStore.instances.length }} {{ instanceStore.instances.length === 1 ? 'instance' : 'instances' }}
          <template v-if="totalPlaytime > 0"> · {{ formatPlaytime(totalPlaytime) }} played</template>
        </p>
      </div>
      <div class="page-header__actions">
        <button class="btn" :disabled="!authStore.authenticated" @click="createModalOpen = true">
          <AppIcon name="plus" :size="14" />
          New instance
        </button>
      </div>
    </header>

    <div v-if="!authStore.authenticated" class="empty empty--centered library__empty">
      <AppIcon name="login" :size="32" class="empty__icon" />
      <p class="empty__title">Sign in to get started</p>
      <p class="empty__hint">Your instances appear here once you are signed in with your Microsoft account.</p>
      <button class="btn" :disabled="authStore.loading" @click="authStore.login">Sign in with Microsoft</button>
    </div>

    <template v-else>
      <div v-if="instanceStore.instances.length > 0" class="library__toolbar">
        <div class="library__search">
          <AppIcon name="search" :size="16" />
          <input v-model="search" type="search" class="input" placeholder="Search instances" aria-label="Search instances" />
        </div>
        <div class="segmented" role="group" aria-label="Sort">
          <button
            v-for="option in sortOptions"
            :key="option.value"
            class="segmented__option"
            :class="{ 'segmented__option--active': sort === option.value }"
            @click="sort = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <div v-if="instanceStore.groups.length > 0" class="chip-row library__groups">
        <button class="chip" :class="{ 'chip--active': group === null }" @click="group = null">All</button>
        <button
          v-for="g in instanceStore.groups"
          :key="g"
          class="chip"
          :class="{ 'chip--active': group === g }"
          @click="group = g"
        >
          {{ g }}
        </button>
      </div>

      <div v-if="instanceStore.error" class="form-error form-error--block">{{ instanceStore.error }}</div>

      <div v-if="instanceStore.loading && !instanceStore.loaded" class="pixel-loader">
        Loading instances
        <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
      </div>

      <div v-else-if="instanceStore.instances.length === 0" class="empty empty--centered library__empty">
        <AppIcon name="box" :size="32" class="empty__icon" />
        <p class="empty__title">No instances yet</p>
        <p class="empty__hint">Create a fresh instance, or install a ready-made modpack from Browse.</p>
        <div class="library__empty-actions">
          <button class="btn" @click="createModalOpen = true">
            <AppIcon name="plus" :size="14" />
            New instance
          </button>
          <RouterLink to="/browse?type=modpack" class="btn btn--secondary">
            <AppIcon name="compass" :size="14" />
            Find a modpack
          </RouterLink>
        </div>
      </div>

      <div v-else-if="visible.length === 0" class="empty empty--centered">
        <p class="empty__title">Nothing matches</p>
        <p class="empty__hint">Try a different search or group.</p>
      </div>

      <div v-else class="library__grid">
        <InstanceCard v-for="instance in visible" :key="instance.id" :instance="instance" @delete="pendingDelete = $event" />
      </div>
    </template>

    <CreateInstanceModal v-if="createModalOpen" @close="createModalOpen = false" @created="handleCreated" />

    <ConfirmDialog
      v-if="pendingDelete"
      title="Delete instance"
      :message="`Delete “${pendingDelete.name}” with all its worlds, mods and settings? This cannot be undone.`"
      confirm-label="Delete"
      danger
      :busy="deleting"
      @cancel="pendingDelete = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import InstanceCard from '@/components/InstanceCard.vue'
import CreateInstanceModal from '@/components/CreateInstanceModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useInstanceStore } from '@/stores/instances'
import { useAuthStore } from '@/stores/auth'
import { useLaunchStore } from '@/stores/launch'
import { useNotificationsStore } from '@/stores/notifications'
import { formatPlaytime } from '@/utils/format'
import type { Instance } from '@/types'

type SortMode = 'recent' | 'name' | 'playtime'

const SORT_KEY = 'pretupid.library.sort'

const router = useRouter()
const instanceStore = useInstanceStore()
const authStore = useAuthStore()
const launchStore = useLaunchStore()
const notifications = useNotificationsStore()

const createModalOpen = ref(false)
const search = ref('')
const group = ref<string | null>(null)
const sort = ref<SortMode>(readSort())
const pendingDelete = ref<Instance | null>(null)
const deleting = ref(false)

const sortOptions: { value: SortMode; label: string }[] = [
  { value: 'recent', label: 'Recent' },
  { value: 'name', label: 'Name' },
  { value: 'playtime', label: 'Most played' }
]

function readSort(): SortMode {
  try {
    const value = localStorage.getItem(SORT_KEY)
    if (value === 'name' || value === 'playtime' || value === 'recent') return value
  } catch {
    void 0
  }
  return 'recent'
}

watch(sort, (value) => {
  try {
    localStorage.setItem(SORT_KEY, value)
  } catch {
    void 0
  }
})

const totalPlaytime = computed(() => instanceStore.instances.reduce((sum, i) => sum + (i.playtime_seconds ?? 0), 0))

const visible = computed(() => {
  const term = search.value.trim().toLowerCase()
  let list = sort.value === 'recent' ? [...instanceStore.recent] : [...instanceStore.instances]
  if (group.value) list = list.filter((i) => i.group_name === group.value)
  if (term) {
    list = list.filter(
      (i) =>
        i.name.toLowerCase().includes(term) ||
        i.minecraft_version.toLowerCase().includes(term) ||
        i.mod_loader.toLowerCase().includes(term)
    )
  }
  if (sort.value === 'name') list.sort((a, b) => a.name.localeCompare(b.name))
  if (sort.value === 'playtime') list.sort((a, b) => (b.playtime_seconds ?? 0) - (a.playtime_seconds ?? 0))
  return list
})

function handleCreated(instance: Instance) {
  router.push({ name: 'instance-detail', params: { id: instance.id } })
}

async function confirmDelete() {
  if (!pendingDelete.value) return
  deleting.value = true
  try {
    const name = pendingDelete.value.name
    await instanceStore.remove(pendingDelete.value.id)
    notifications.notifySuccess('Instance deleted', `${name} was removed.`)
    pendingDelete.value = null
  } catch (e) {
    notifications.notifyError('Delete failed', String(e))
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  await launchStore.initListeners()
  await authStore.check()
  if (authStore.authenticated) await instanceStore.load(instanceStore.loaded)
})
</script>

<style scoped lang="scss">
.library {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.library__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.library__search {
  position: relative;
  flex: 1;
  min-width: 220px;
  max-width: 420px;
  color: var(--text-secondary);

  .app-icon {
    position: absolute;
    top: 50%;
    left: 0.85rem;
    transform: translateY(-50%);
  }

  .input {
    padding-left: 2.5rem;
  }
}

.library__empty {
  padding: 3rem 1.5rem;
}

.library__empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.library__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}
</style>
