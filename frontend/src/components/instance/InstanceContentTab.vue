<template>
  <section class="content-tab" :class="{ 'content-tab--dropping': dropping }">
    <div v-if="dropping" class="content-tab__drop">
      <AppIcon name="upload" :size="32" />
      <span>Drop .jar or .zip files to add them</span>
    </div>

    <div class="content-tab__toolbar">
      <div class="segmented" role="tablist" aria-label="Content type">
        <button
          v-for="filter in filters"
          :key="filter.value"
          class="segmented__option"
          :class="{ 'segmented__option--active': typeFilter === filter.value }"
          @click="typeFilter = filter.value"
        >
          <AppIcon :name="filter.icon" :size="12" />
          {{ filter.label }}
          <span class="content-tab__count">{{ countFor(filter.value) }}</span>
        </button>
      </div>
      <div class="content-tab__actions">
        <button class="btn btn--small" :disabled="busy" @click="browse">
          <AppIcon name="compass" :size="12" />
          Find content
        </button>
        <button class="btn btn--secondary btn--small" :disabled="busy" @click="pickFiles">
          <AppIcon name="upload" :size="12" />
          Add files
        </button>
        <button class="btn btn--secondary btn--small" :disabled="busy || entries.length === 0" @click="checkUpdates">
          <AppIcon name="refresh" :size="12" />
          {{ checking ? 'Checking' : 'Check updates' }}
        </button>
      </div>
    </div>

    <div v-if="updates.length > 0" class="panel panel--compact panel--accent content-tab__updates">
      <AppIcon name="arrow-up" :size="18" />
      <span class="content-tab__updates-text">
        {{ updates.length }} update{{ updates.length === 1 ? '' : 's' }} available
      </span>
      <button class="btn btn--small" :disabled="busy" @click="applyUpdates(null)">
        {{ updating ? 'Updating' : 'Update all' }}
      </button>
    </div>

    <PretupidClientCard :instance="instance" :locked="busy" :revision="revision" @installed="load" />

    <p v-if="instance.mod_loader === 'vanilla'" class="content-tab__note">
      <AppIcon name="info" :size="14" />
      This is a vanilla instance, so it can use resource packs but not mods. Create a Fabric or Forge instance to add
      mods.
    </p>

    <div v-if="entries.length > 0" class="content-tab__search">
      <AppIcon name="search" :size="14" />
      <input v-model="search" type="search" class="input" placeholder="Filter installed content" aria-label="Filter installed content" />
    </div>

    <div v-if="loading && entries.length === 0" class="pixel-loader">
      Reading content
      <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
    </div>

    <div v-else-if="entries.length === 0" class="empty empty--centered">
      <AppIcon name="puzzle" :size="30" class="empty__icon" />
      <p class="empty__title">Nothing installed yet</p>
      <p class="empty__hint">Find mods, resource packs and shaders on Browse, or drag files onto this page.</p>
      <button class="btn btn--small" @click="browse">Find content</button>
    </div>

    <div v-else-if="visible.length === 0" class="empty empty--centered">
      <p class="empty__title">No matches</p>
      <p class="empty__hint">Try a different filter.</p>
    </div>

    <ul v-else class="list content-tab__list">
      <li
        v-for="entry in visible"
        :key="entry.id"
        class="list__item content-tab__item"
        :class="{ 'content-tab__item--disabled': !entry.enabled }"
      >
        <div class="list__icon">
          <img v-if="entry.icon_url" :src="entry.icon_url" :alt="''" loading="lazy" />
          <AppIcon v-else :name="iconFor(entry.project_type)" :size="18" />
        </div>
        <div class="list__body">
          <span class="content-tab__title">{{ entry.title }}</span>
          <span class="list__meta">
            {{ typeLabel(entry.project_type) }}
            <template v-if="entry.version_name"> · {{ entry.version_name }}</template>
            <template v-if="entry.project_id.startsWith('local:')"> · added manually</template>
          </span>
        </div>
        <button
          v-if="updateFor(entry.id)"
          class="btn btn--small content-tab__update"
          :disabled="busy"
          :title="`Update to ${updateFor(entry.id)?.new_version_name}`"
          @click="applyUpdates([entry.id])"
        >
          <AppIcon name="arrow-up" :size="12" />
          {{ updateFor(entry.id)?.new_version_name }}
        </button>
        <label class="toggle" :title="entry.enabled ? 'Enabled' : 'Disabled'">
          <input
            type="checkbox"
            class="toggle__input"
            :checked="entry.enabled"
            :disabled="busy || pendingIds.has(entry.id)"
            :aria-label="`${entry.enabled ? 'Disable' : 'Enable'} ${entry.title}`"
            @change="toggle(entry)"
          />
          <span class="toggle__track" />
        </label>
        <button
          class="icon-btn icon-btn--small icon-btn--danger"
          :disabled="busy || pendingIds.has(entry.id)"
          :aria-label="`Remove ${entry.title}`"
          title="Remove"
          @click="remove(entry)"
        >
          <AppIcon name="trash" :size="14" />
        </button>
      </li>
    </ul>

    <p v-if="entries.length > 0" class="content-tab__summary">
      {{ enabledCount }} of {{ entries.length }} enabled · Disabled items stay installed but are not loaded.
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { open } from '@tauri-apps/plugin-dialog'
import { getCurrentWebview } from '@tauri-apps/api/webview'
import type { UnlistenFn } from '@tauri-apps/api/event'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import PretupidClientCard from '@/components/instance/PretupidClientCard.vue'
import {
  checkInstanceUpdates,
  importInstanceFiles,
  listInstanceMods,
  removeInstanceMod,
  setInstanceModEnabled,
  updateInstanceMods
} from '@/api'
import { useNotificationsStore } from '@/stores/notifications'
import type { Instance, InstanceMod, ModUpdate, InstallResult } from '@/types'

type Filter = 'all' | 'mod' | 'resourcepack' | 'shader'

const props = defineProps<{ instance: Instance; locked: boolean }>()
const emit = defineEmits<{ (e: 'count', value: number): void }>()

const router = useRouter()
const notifications = useNotificationsStore()

const entries = ref<InstanceMod[]>([])
const updates = ref<ModUpdate[]>([])
const loading = ref(false)
const checking = ref(false)
const updating = ref(false)
const importing = ref(false)
const dropping = ref(false)
const search = ref('')
const typeFilter = ref<Filter>('all')
const pendingIds = ref(new Set<string>())
const revision = ref(0)
let unlistenDrop: UnlistenFn | null = null

const busy = computed(() => props.locked || checking.value || updating.value || importing.value)

const filters: { value: Filter; label: string; icon: IconName }[] = [
  { value: 'all', label: 'All', icon: 'layers' },
  { value: 'mod', label: 'Mods', icon: 'puzzle' },
  { value: 'resourcepack', label: 'Resource packs', icon: 'image' },
  { value: 'shader', label: 'Shaders', icon: 'sparkle' }
]

const enabledCount = computed(() => entries.value.filter((e) => e.enabled).length)

const visible = computed(() => {
  const term = search.value.trim().toLowerCase()
  return entries.value.filter((e) => {
    if (typeFilter.value !== 'all' && e.project_type !== typeFilter.value) return false
    if (!term) return true
    return e.title.toLowerCase().includes(term) || (e.file_name ?? '').toLowerCase().includes(term)
  })
})

function countFor(filter: Filter): number {
  if (filter === 'all') return entries.value.length
  return entries.value.filter((e) => e.project_type === filter).length
}

function iconFor(type: string): IconName {
  if (type === 'resourcepack') return 'image'
  if (type === 'shader') return 'sparkle'
  return 'puzzle'
}

function typeLabel(type: string): string {
  if (type === 'resourcepack') return 'Resource pack'
  if (type === 'shader') return 'Shader'
  return 'Mod'
}

function updateFor(id: string): ModUpdate | undefined {
  return updates.value.find((u) => u.mod_id === id)
}

async function load() {
  loading.value = true
  try {
    entries.value = await listInstanceMods(props.instance.id)
    revision.value++
    emit('count', entries.value.length)
  } catch (e) {
    notifications.notifyError('Could not read content', String(e))
  } finally {
    loading.value = false
  }
}

function reportResult(result: InstallResult, verb: string) {
  if (result.installed.length > 0) {
    notifications.notifySuccess(
      `${verb} ${result.installed.length} item${result.installed.length === 1 ? '' : 's'}`,
      result.installed.map((m) => m.title).slice(0, 4).join(', ')
    )
  }
  if (result.warnings.length > 0) {
    notifications.notifyWarning('Some items need attention', result.warnings.slice(0, 3).join(' · '))
  }
}

async function toggle(entry: InstanceMod) {
  pendingIds.value.add(entry.id)
  try {
    const updated = await setInstanceModEnabled(entry.id, !entry.enabled)
    entries.value = entries.value.map((e) => (e.id === updated.id ? updated : e))
  } catch (e) {
    notifications.notifyError('Could not change state', String(e))
  } finally {
    pendingIds.value.delete(entry.id)
  }
}

async function remove(entry: InstanceMod) {
  pendingIds.value.add(entry.id)
  try {
    await removeInstanceMod(entry.id)
    entries.value = entries.value.filter((e) => e.id !== entry.id)
    updates.value = updates.value.filter((u) => u.mod_id !== entry.id)
    emit('count', entries.value.length)
    notifications.notifySuccess('Removed', `${entry.title} was removed.`)
  } catch (e) {
    notifications.notifyError('Could not remove', String(e))
  } finally {
    pendingIds.value.delete(entry.id)
  }
}

async function checkUpdates() {
  checking.value = true
  try {
    updates.value = await checkInstanceUpdates(props.instance.id)
    await load()
    if (updates.value.length === 0) notifications.notifySuccess('All up to date', 'Every item is on its newest compatible version.')
  } catch (e) {
    notifications.notifyError('Update check failed', String(e))
  } finally {
    checking.value = false
  }
}

async function applyUpdates(ids: string[] | null) {
  updating.value = true
  try {
    const result = await updateInstanceMods(props.instance.id, ids)
    const done = new Set(result.installed.map((m) => m.id))
    updates.value = updates.value.filter((u) => !done.has(u.mod_id))
    reportResult(result, 'Updated')
    await load()
  } catch (e) {
    notifications.notifyError('Update failed', String(e))
  } finally {
    updating.value = false
  }
}

async function importPaths(paths: string[]) {
  const usable = paths.filter((p) => /\.(jar|zip)$/i.test(p))
  if (usable.length === 0) {
    notifications.notifyWarning('Nothing to add', 'Only .jar mods and .zip resource or shader packs can be added here.')
    return
  }
  importing.value = true
  try {
    const result = await importInstanceFiles(props.instance.id, usable)
    reportResult(result, 'Added')
    await load()
  } catch (e) {
    notifications.notifyError('Import failed', String(e))
  } finally {
    importing.value = false
  }
}

async function pickFiles() {
  try {
    const selected = await open({
      multiple: true,
      directory: false,
      filters: [{ name: 'Mods and packs', extensions: ['jar', 'zip'] }]
    })
    const paths = Array.isArray(selected) ? selected : typeof selected === 'string' ? [selected] : []
    if (paths.length > 0) await importPaths(paths)
  } catch (e) {
    notifications.notifyError('Could not open file picker', String(e))
  }
}

function browse() {
  const type = typeFilter.value === 'all' ? (props.instance.mod_loader === 'vanilla' ? 'resourcepack' : 'mod') : typeFilter.value
  router.push({ name: 'browse', query: { instance: props.instance.id, type } })
}

watch(
  () => props.instance.id,
  () => {
    updates.value = []
    load()
  }
)

onMounted(async () => {
  await load()
  try {
    unlistenDrop = await getCurrentWebview().onDragDropEvent((event) => {
      const payload = event.payload
      if (payload.type === 'enter' || payload.type === 'over') dropping.value = true
      else if (payload.type === 'leave') dropping.value = false
      else if (payload.type === 'drop') {
        dropping.value = false
        if (!busy.value) importPaths(payload.paths)
      }
    })
  } catch {
    unlistenDrop = null
  }
})

onBeforeUnmount(() => {
  if (unlistenDrop) unlistenDrop()
})

defineExpose({ reload: load })
</script>

<style scoped lang="scss">
.content-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.content-tab__drop {
  position: absolute;
  inset: -0.5rem;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: var(--accent);
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  background-color: color-mix(in srgb, var(--bg-primary) 88%, transparent);
  border: 3px dashed var(--accent);
  border-radius: var(--edge-radius);
  pointer-events: none;
}

.content-tab__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.content-tab__count {
  opacity: 0.7;
}

.content-tab__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.content-tab__updates {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: var(--accent);
}

.content-tab__updates-text {
  flex: 1;
  color: var(--text-primary);
}

.content-tab__note {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  color: var(--text-secondary);
}

.content-tab__search {
  position: relative;
  max-width: 360px;
  color: var(--text-secondary);

  .app-icon {
    position: absolute;
    top: 50%;
    left: 0.8rem;
    transform: translateY(-50%);
  }

  .input {
    padding: 0.55rem 0.8rem 0.55rem 2.2rem;
  }
}

.content-tab__item {
  transition: opacity var(--transition-fast);

  .list__icon {
    color: var(--text-secondary);
  }
}

.content-tab__item--disabled {
  opacity: 0.55;
}

.content-tab__title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 1.15rem;
  color: var(--text-primary);
}

.content-tab__update {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.content-tab__summary {
  margin: 0;
  font-size: 0.95rem;
  color: var(--text-secondary);
}
</style>
