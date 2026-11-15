<template>
  <div class="instance-view">
    <RouterLink to="/library" class="instance-view__back">
      <AppIcon name="back" :size="14" />
      Library
    </RouterLink>

    <div v-if="loadError" class="form-error form-error--block">{{ loadError }}</div>

    <template v-if="instance">
      <header class="instance-view__header">
        <PixelScenery :seed="instance.id" class="instance-view__scenery" />
        <InstanceIcon :instance="instance" :size="88" class="instance-view__icon" />
        <div class="instance-view__heading">
          <h1 class="instance-view__title" :title="instance.name">{{ instance.name }}</h1>
          <div class="instance-view__tags">
            <span class="badge">Minecraft {{ instance.minecraft_version }}</span>
            <span class="badge" :class="instance.mod_loader === 'vanilla' ? '' : 'badge--accent'">
              {{ instance.loader_version ? `${loaderLabel(instance.mod_loader)} ${instance.loader_version}` : loaderLabel(instance.mod_loader) }}
            </span>
            <span v-if="instance.group_name" class="badge">{{ instance.group_name }}</span>
          </div>
          <p class="instance-view__meta">
            <span><AppIcon name="clock" :size="12" /> {{ formatPlaytime(instance.playtime_seconds) }}</span>
            <span>{{ instance.last_played ? `Last played ${formatRelative(instance.last_played).toLowerCase()}` : 'Not played yet' }}</span>
          </p>
        </div>
        <div class="instance-view__actions">
          <PlayButton :instance-id="instance.id" size="large" />
          <button class="icon-btn" title="Open folder" aria-label="Open instance folder" @click="openFolder()">
            <AppIcon name="folder" :size="18" />
          </button>
          <DropdownMenu>
            <template #trigger="{ toggle, open: menuOpen }">
              <button class="icon-btn" aria-label="More actions" title="More actions" aria-haspopup="menu" :aria-expanded="menuOpen" @click="toggle">
                <AppIcon name="more" :size="18" />
              </button>
            </template>
            <template #default="{ close }">
              <button class="menu__item" :disabled="locked" @click="run(close, downloadNow)">
                <AppIcon name="download" :size="14" />Download game files now
              </button>
              <button class="menu__item" :disabled="locked" @click="run(close, repair)">
                <AppIcon name="wrench" :size="14" />Repair files
              </button>
              <button class="menu__item" :disabled="locked" @click="run(close, exportPack)">
                <AppIcon name="upload" :size="14" />Export as .mrpack
              </button>
              <button class="menu__item" :disabled="locked" @click="run(close, duplicate)">
                <AppIcon name="copy" :size="14" />Duplicate
              </button>
              <div class="menu__divider" />
              <button class="menu__item" @click="run(close, () => openFolder('mods'))"><AppIcon name="folder" :size="14" />Mods folder</button>
              <button class="menu__item" @click="run(close, () => openFolder('saves'))"><AppIcon name="folder" :size="14" />Worlds folder</button>
              <button class="menu__item" @click="run(close, () => openFolder('screenshots'))"><AppIcon name="image" :size="14" />Screenshots</button>
              <div class="menu__divider" />
              <button class="menu__item menu__item--danger" :disabled="locked" @click="run(close, () => (confirmDelete = true))">
                <AppIcon name="trash" :size="14" />Delete instance
              </button>
            </template>
          </DropdownMenu>
        </div>
      </header>

      <div v-if="installState" class="panel panel--compact" :class="installState.status === 'error' ? 'panel--danger' : 'panel--accent'">
        <div class="instance-view__banner">
          <span class="badge" :class="installState.status === 'error' ? 'badge--danger' : 'badge--accent'">
            {{ installs.statusLabel(installState.status) }}
          </span>
          <span>{{ installState.message }}</span>
        </div>
        <div v-if="installing" class="progress-bar progress-bar--indeterminate instance-view__banner-bar">
          <div class="progress-bar__fill" />
        </div>
      </div>

      <div v-if="state.lastError && !state.busy" class="panel panel--compact panel--danger instance-view__error">
        <AppIcon name="alert" :size="16" />
        <span>{{ state.lastError }}</span>
        <button class="icon-btn icon-btn--small" aria-label="Dismiss" @click="state.lastError = null">
          <AppIcon name="x" :size="14" />
        </button>
      </div>

      <div v-if="repairReport" class="panel panel--compact" :class="repairReport.failed.length ? 'panel--danger' : 'panel--success'">
        <div class="instance-view__banner">
          <span>
            Repair finished: {{ repairReport.repaired }} fixed, {{ repairReport.healthy }} already fine
            <template v-if="repairReport.failed.length">, {{ repairReport.failed.length }} could not be fixed</template>.
          </span>
          <button class="icon-btn icon-btn--small" aria-label="Dismiss" @click="repairReport = null">
            <AppIcon name="x" :size="14" />
          </button>
        </div>
        <ul v-if="repairReport.failed.length" class="instance-view__failures">
          <li v-for="failure in repairReport.failed" :key="failure.title + failure.file_name">
            <strong>{{ failure.title }}</strong> — {{ failure.reason }}
          </li>
        </ul>
      </div>

      <nav class="tabs" role="tablist">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="tabs__tab"
          :class="{ 'tabs__tab--active': activeTab === tab.id }"
          role="tab"
          :aria-selected="activeTab === tab.id"
          @click="setTab(tab.id)"
        >
          <AppIcon :name="tab.icon" :size="14" />
          {{ tab.label }}
          <span v-if="tab.id === 'content' && contentCount !== null" class="tabs__count">{{ contentCount }}</span>
          <span v-if="tab.id === 'console' && state.running" class="instance-view__live" />
        </button>
      </nav>

      <InstanceContentTab
        v-if="activeTab === 'content'"
        :instance="instance"
        :locked="locked"
        @count="contentCount = $event"
      />
      <InstanceSettingsTab v-else-if="activeTab === 'settings'" :instance="instance" @updated="instance = $event" />
      <InstanceConsoleTab v-else :instance-id="instance.id" />
    </template>

    <div v-else-if="!loadError" class="pixel-loader">
      Loading
      <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
    </div>

    <ConfirmDialog
      v-if="confirmDelete && instance"
      title="Delete instance"
      :message="`Delete “${instance.name}” with all its worlds, mods and settings? This cannot be undone.`"
      confirm-label="Delete"
      danger
      :busy="deleting"
      @cancel="confirmDelete = false"
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import PlayButton from '@/components/PlayButton.vue'
import PixelScenery from '@ui/PixelScenery.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import InstanceContentTab from '@/components/instance/InstanceContentTab.vue'
import InstanceSettingsTab from '@/components/instance/InstanceSettingsTab.vue'
import InstanceConsoleTab from '@/components/instance/InstanceConsoleTab.vue'
import { exportInstanceModpack, getInstance, openInstanceFolder, repairInstanceMods, type RepairReport } from '@/api'
import { useLaunchStore } from '@/stores/launch'
import { useInstallsStore } from '@/stores/installs'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import { formatPlaytime, formatRelative, loaderLabel } from '@/utils/format'
import type { Instance } from '@/types'

type TabId = 'content' | 'settings' | 'console'

const route = useRoute()
const router = useRouter()
const launchStore = useLaunchStore()
const installs = useInstallsStore()
const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()

const instance = ref<Instance | null>(null)
const loadError = ref<string | null>(null)
const confirmDelete = ref(false)
const deleting = ref(false)
const repairReport = ref<RepairReport | null>(null)
const contentCount = ref<number | null>(null)
const working = ref(false)

const tabs: { id: TabId; label: string; icon: IconName }[] = [
  { id: 'content', label: 'Content', icon: 'puzzle' },
  { id: 'settings', label: 'Settings', icon: 'sliders' },
  { id: 'console', label: 'Console', icon: 'terminal' }
]

function readTab(): TabId {
  const tab = route.query.tab
  return tab === 'settings' || tab === 'console' ? tab : 'content'
}

const activeTab = ref<TabId>(readTab())

launchStore.ensureState(String(route.params.id))
const state = computed(() => launchStore.getState(String(route.params.id)))
const installState = computed(() => (instance.value ? installs.getState(instance.value.id) : null))
const installing = computed(() => (instance.value ? installs.isInstalling(instance.value.id) : false))
const locked = computed(() => working.value || installing.value || state.value.busy)

function setTab(tab: TabId) {
  activeTab.value = tab
  router.replace({ query: { ...route.query, tab } }).catch(() => void 0)
}

watch(
  () => state.value.running,
  (running) => {
    if (!running) refresh().catch(() => void 0)
  }
)

watch(
  () => state.value.crash,
  (crash) => {
    if (crash) activeTab.value = 'console'
  }
)

async function refresh() {
  const id = String(route.params.id)
  const loaded = await getInstance(id)
  if (!loaded) {
    loadError.value = 'This instance no longer exists.'
    instance.value = null
    return
  }
  instance.value = loaded
}

function run(close: () => void, action: () => unknown) {
  close()
  action()
}

async function openFolder(folder?: string) {
  if (!instance.value) return
  try {
    await openInstanceFolder(instance.value.id, folder)
  } catch (e) {
    notifications.notifyError('Could not open folder', String(e))
  }
}

async function downloadNow() {
  if (!instance.value) return
  try {
    await launchStore.prepare(instance.value.id)
    notifications.notifySuccess('Ready to play', `${instance.value.name} is fully downloaded.`)
  } catch (e) {
    notifications.notifyError('Download failed', String(e))
  }
}

async function repair() {
  if (!instance.value) return
  working.value = true
  repairReport.value = null
  try {
    repairReport.value = await repairInstanceMods(instance.value.id)
    working.value = false
    await launchStore.prepare(instance.value.id, true)
    notifications.notifySuccess('Repair complete', 'Game files and content were verified.')
  } catch (e) {
    notifications.notifyError('Repair failed', String(e))
  } finally {
    working.value = false
  }
}

async function exportPack() {
  if (!instance.value) return
  working.value = true
  try {
    const path = await exportInstanceModpack(instance.value.id)
    notifications.notifySuccess('Exported', `Saved to ${path}`)
  } catch (e) {
    notifications.notifyError('Export failed', String(e))
  } finally {
    working.value = false
  }
}

async function duplicate() {
  if (!instance.value) return
  working.value = true
  try {
    const copy = await instanceStore.duplicate(instance.value.id)
    notifications.notifySuccess('Duplicated', `${copy.name} was created.`)
    router.push({ name: 'instance-detail', params: { id: copy.id } })
  } catch (e) {
    notifications.notifyError('Duplicate failed', String(e))
  } finally {
    working.value = false
  }
}

async function handleDelete() {
  if (!instance.value) return
  deleting.value = true
  try {
    const name = instance.value.name
    await instanceStore.remove(instance.value.id)
    notifications.notifySuccess('Instance deleted', `${name} was removed.`)
    router.push({ name: 'library' })
  } catch (e) {
    notifications.notifyError('Delete failed', String(e))
  } finally {
    deleting.value = false
    confirmDelete.value = false
  }
}

onMounted(async () => {
  await launchStore.initListeners()
  try {
    await refresh()
  } catch (e) {
    loadError.value = String(e)
  }
})

</script>

<style scoped lang="scss">
.instance-view {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.instance-view__back {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  align-self: flex-start;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-secondary);
  text-decoration: none;

  &:hover {
    color: var(--accent);
  }
}

.instance-view__header {
  position: relative;
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 1.25rem;
  min-height: 190px;
  padding: 1.5rem;
  overflow: hidden;
  isolation: isolate;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);

  > :not(.instance-view__scenery) {
    position: relative;
    z-index: 1;
  }
}

.instance-view__scenery {
  z-index: 0;
}

.instance-view__icon {
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.45);
}

.instance-view__heading {
  flex: 1;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.instance-view__title {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 1.6rem;
  line-height: 1.25;
  color: #ffffff;
  text-shadow: 0 2px 0 rgba(0, 0, 0, 0.35), 0 4px 18px rgba(0, 0, 0, 0.5);
}

.instance-view__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.instance-view__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin: 0;
  font-size: 0.88rem;
  color: rgba(255, 255, 255, 0.82);
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.6);

  span {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
  }
}

.instance-view__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;

  .icon-btn {
    color: #ffffff;
    background-color: rgba(0, 0, 0, 0.38);
    border-color: rgba(255, 255, 255, 0.14);
    backdrop-filter: blur(6px);
  }
}

.instance-view__banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: var(--text-primary);

  span:not(.badge) {
    flex: 1;
  }
}

.instance-view__banner-bar {
  margin-top: 0.6rem;
}

.instance-view__error {
  display: flex;
  align-items: center;
  gap: 0.6rem;

  span {
    flex: 1;
    word-break: break-word;
  }
}

.instance-view__failures {
  margin: 0.6rem 0 0;
  padding-left: 1.1rem;
  color: var(--text-secondary);
}

.instance-view__live {
  width: 8px;
  height: 8px;
  background-color: var(--success);
}
</style>
