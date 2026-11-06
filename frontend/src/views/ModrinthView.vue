<template>
  <div class="browse">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">Browse</h1>
        <p class="page-header__subtitle">Mods, modpacks, resource packs and shaders from Modrinth.</p>
      </div>
    </header>

    <section class="browse__target panel panel--compact">
      <AppIcon name="box" :size="18" />
      <label class="browse__target-label" for="browse-target">Install into</label>
      <select id="browse-target" v-model="targetId" class="input browse__target-select" :disabled="projectType === 'modpack'">
        <option value="">{{ projectType === 'modpack' ? 'Modpacks create a new instance' : 'Ask me each time' }}</option>
        <option v-for="instance in instanceStore.recent" :key="instance.id" :value="instance.id">
          {{ instance.name }} — {{ instance.minecraft_version }} {{ loaderLabel(instance.mod_loader) }}
        </option>
      </select>
      <label v-if="target && projectType !== 'modpack'" class="toggle">
        <input v-model="compatibleOnly" type="checkbox" class="toggle__input" />
        <span class="toggle__track" />
        <span class="toggle__label">Only show compatible</span>
      </label>
    </section>

    <div class="browse__controls">
      <div class="segmented" role="tablist" aria-label="Project type">
        <button
          v-for="type in projectTypes"
          :key="type.value"
          class="segmented__option"
          :class="{ 'segmented__option--active': projectType === type.value }"
          @click="setType(type.value)"
        >
          <AppIcon :name="type.icon" :size="12" />
          {{ type.label }}
        </button>
      </div>
      <div class="browse__search">
        <AppIcon name="search" :size="16" />
        <input
          v-model="query"
          type="search"
          class="input"
          :placeholder="`Search ${currentTypeLabel.toLowerCase()}`"
          aria-label="Search Modrinth"
        />
      </div>
      <select v-model="sortIndex" class="input browse__sort" aria-label="Sort by">
        <option value="relevance">Most relevant</option>
        <option value="downloads">Most downloaded</option>
        <option value="follows">Most followed</option>
        <option value="updated">Recently updated</option>
        <option value="newest">Newest</option>
      </select>
      <button class="btn btn--secondary btn--small" :class="{ 'btn--active': filtersOpen }" @click="filtersOpen = !filtersOpen">
        <AppIcon name="sliders" :size="12" />
        Filters<template v-if="activeFilterCount"> ({{ activeFilterCount }})</template>
      </button>
    </div>

    <div v-if="filtersOpen" class="panel panel--compact browse__filters">
      <div v-if="!target || !compatibleOnly || projectType === 'modpack'" class="browse__filter-row">
        <span class="form-label">Minecraft</span>
        <select v-model="gameVersion" class="input browse__filter-select">
          <option value="">Any version</option>
          <option v-for="gv in releaseVersions" :key="gv.version" :value="gv.version">{{ gv.version }}</option>
        </select>
        <template v-if="projectType === 'mod' || projectType === 'modpack'">
          <span class="form-label">Loader</span>
          <select v-model="loaderFilter" class="input browse__filter-select">
            <option value="">Any loader</option>
            <option value="fabric">Fabric</option>
            <option value="quilt">Quilt</option>
            <option value="forge">Forge</option>
            <option value="neoforge">NeoForge</option>
          </select>
        </template>
      </div>
      <div class="browse__filter-row browse__filter-row--wrap">
        <span class="form-label">Categories</span>
        <div class="chip-row">
          <button
            v-for="cat in categoryOptions"
            :key="cat"
            class="chip"
            :class="{ 'chip--active': categories.includes(cat) }"
            @click="toggleCategory(cat)"
          >
            {{ prettyCategory(cat) }}
          </button>
        </div>
      </div>
      <button v-if="activeFilterCount" class="btn btn--ghost btn--small browse__clear" @click="clearFilters">Clear filters</button>
    </div>

    <div v-if="modrinthStore.error" class="form-error form-error--block">{{ modrinthStore.error }}</div>

    <div v-if="modrinthStore.loading && hits.length === 0" class="browse__grid">
      <div v-for="n in 8" :key="n" class="browse__card browse__card--skeleton" />
    </div>

    <div v-else-if="hits.length === 0" class="empty empty--centered">
      <AppIcon name="search" :size="28" class="empty__icon" />
      <p class="empty__title">No results</p>
      <p class="empty__hint">Try another search term or loosen the filters.</p>
    </div>

    <div v-else class="browse__grid">
      <article
        v-for="project in hits"
        :key="project.project_id"
        class="browse__card"
        tabindex="0"
        @click="openDetails(project)"
        @keydown.enter="openDetails(project)"
      >
        <div class="browse__card-top">
          <div class="list__icon browse__card-icon">
            <img v-if="project.icon_url" :src="project.icon_url" alt="" loading="lazy" />
            <AppIcon v-else name="puzzle" :size="22" />
          </div>
          <div class="browse__card-heading">
            <h3 class="browse__card-title">{{ project.title }}</h3>
            <span class="browse__card-author">by {{ project.author }}</span>
          </div>
        </div>
        <p class="browse__card-desc">{{ project.description }}</p>
        <div class="browse__card-footer">
          <span class="browse__card-stat"><AppIcon name="download" :size="12" />{{ formatCount(project.downloads) }}</span>
          <span class="browse__card-stat"><AppIcon name="clock" :size="12" />{{ formatRelative(project.date_modified) }}</span>
          <span class="browse__card-spacer" />
          <button
            v-if="installedIds.has(project.project_id)"
            class="btn btn--secondary btn--small"
            disabled
            @click.stop
          >
            <AppIcon name="check" :size="12" />
            Installed
          </button>
          <button
            v-else
            class="btn btn--small"
            :disabled="installingId === project.project_id || !!cardBlocker(project)"
            :title="cardBlocker(project) ?? ''"
            @click.stop="quickInstall(project)"
          >
            <AppIcon :name="project.project_type === 'modpack' ? 'plus' : 'download'" :size="12" />
            {{ installingId === project.project_id ? 'Installing' : 'Install' }}
          </button>
        </div>
      </article>
    </div>

    <div v-if="modrinthStore.hasMore && hits.length > 0" class="browse__more">
      <button class="btn btn--secondary" :disabled="modrinthStore.loading" @click="modrinthStore.loadMore()">
        {{ modrinthStore.loading ? 'Loading' : 'Load more' }}
      </button>
    </div>

    <ProjectDetailsModal
      v-if="detailsProject"
      :project="detailsProject"
      :target="target"
      :installed="installedIds.has(detailsProject.project_id)"
      @close="detailsProject = null"
      @install="(versionId, deps) => install(detailsProject!, versionId, deps)"
    />

    <InstancePickerModal
      v-if="pickerProject"
      :title="pickerProject.project.title"
      :project-type="pickerProject.project.project_type"
      :game-versions="pickerProject.project.versions"
      :loaders="projectLoaders(pickerProject.project)"
      :busy="!!installingId"
      @close="pickerProject = null"
      @pick="pickInstance"
    />

    <CreateModpackInstanceModal
      v-if="modpackProject"
      :project="modpackProject"
      @close="modpackProject = null"
      @created="modpackProject = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import CreateModpackInstanceModal from '@/components/CreateModpackInstanceModal.vue'
import InstancePickerModal from '@/components/InstancePickerModal.vue'
import ProjectDetailsModal from '@/components/ProjectDetailsModal.vue'
import { useModrinthStore } from '@/stores/modrinth'
import { useInstanceStore } from '@/stores/instances'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { addInstanceMod, listInstanceMods } from '@/api'
import { compatibility, instanceModLoaders } from '@/utils/compat'
import { formatCount, formatRelative, loaderLabel } from '@/utils/format'
import type { Instance, ModrinthSearchResult } from '@/types'

type ProjectType = 'mod' | 'modpack' | 'resourcepack' | 'shader'

const route = useRoute()
const router = useRouter()
const modrinthStore = useModrinthStore()
const instanceStore = useInstanceStore()
const authStore = useAuthStore()
const notifications = useNotificationsStore()

const projectTypes: { value: ProjectType; label: string; icon: IconName }[] = [
  { value: 'mod', label: 'Mods', icon: 'puzzle' },
  { value: 'modpack', label: 'Modpacks', icon: 'box' },
  { value: 'resourcepack', label: 'Resource packs', icon: 'image' },
  { value: 'shader', label: 'Shaders', icon: 'sparkle' }
]

function readType(): ProjectType {
  const t = route.query.type
  return t === 'modpack' || t === 'resourcepack' || t === 'shader' ? t : 'mod'
}

const projectType = ref<ProjectType>(readType())
const targetId = ref<string>(typeof route.query.instance === 'string' ? route.query.instance : '')
const compatibleOnly = ref(true)
const query = ref('')
const sortIndex = ref('relevance')
const filtersOpen = ref(false)
const gameVersion = ref('')
const loaderFilter = ref('')
const categories = ref<string[]>([])
const installedIds = ref(new Set<string>())
const installingId = ref<string | null>(null)
const detailsProject = ref<ModrinthSearchResult | null>(null)
const pickerProject = ref<{ project: ModrinthSearchResult; versionId: string | null; deps: boolean } | null>(null)
const modpackProject = ref<ModrinthSearchResult | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const target = computed<Instance | null>(() => (targetId.value ? instanceStore.byId(targetId.value) ?? null : null))
const hits = computed(() => modrinthStore.searchResults?.hits ?? [])
const currentTypeLabel = computed(() => projectTypes.find((t) => t.value === projectType.value)?.label ?? 'Projects')
const releaseVersions = computed(() => modrinthStore.gameVersions.filter((v) => v.version_type === 'release'))
const categoryOptions = computed(() =>
  modrinthStore.categories
    .filter((c) => c.project_type === projectType.value && c.header !== 'resolutions' && c.header !== 'performance impact')
    .map((c) => c.name)
)
const activeFilterCount = computed(() => categories.value.length + (gameVersion.value ? 1 : 0) + (loaderFilter.value ? 1 : 0))

function prettyCategory(value: string): string {
  return value.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

function projectLoaders(project: ModrinthSearchResult): string[] {
  return project.categories.filter((c) => ['fabric', 'quilt', 'forge', 'neoforge'].includes(c))
}

function cardBlocker(project: ModrinthSearchResult): string | null {
  if (project.project_type === 'modpack' || !target.value) return null
  return compatibility(target.value, project.project_type, project.versions, projectLoaders(project))
}

function buildFacets(): string {
  const groups: string[][] = [[`project_type:${projectType.value}`]]
  const useTarget = target.value && compatibleOnly.value && projectType.value !== 'modpack'
  if (useTarget && target.value) {
    groups.push([`versions:${target.value.minecraft_version}`])
    if (projectType.value === 'mod') {
      groups.push(instanceModLoaders(target.value).map((l) => `categories:${l}`))
    }
  } else {
    if (gameVersion.value) groups.push([`versions:${gameVersion.value}`])
    if (loaderFilter.value && (projectType.value === 'mod' || projectType.value === 'modpack')) {
      groups.push([`categories:${loaderFilter.value}`])
    }
  }
  for (const cat of categories.value) groups.push([`categories:${cat}`])
  return JSON.stringify(groups)
}

function runSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = null
  modrinthStore.search(query.value.trim(), buildFacets(), 0, 24, sortIndex.value)
}

function scheduleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(runSearch, 350)
}

function setType(type: ProjectType) {
  if (projectType.value === type) return
  projectType.value = type
  categories.value = []
}

function toggleCategory(cat: string) {
  categories.value = categories.value.includes(cat) ? categories.value.filter((c) => c !== cat) : [...categories.value, cat]
}

function clearFilters() {
  categories.value = []
  gameVersion.value = ''
  loaderFilter.value = ''
}

async function refreshInstalled() {
  if (!target.value) {
    installedIds.value = new Set()
    return
  }
  try {
    const entries = await listInstanceMods(target.value.id)
    installedIds.value = new Set(entries.map((e) => e.project_id))
  } catch {
    installedIds.value = new Set()
  }
}

function openDetails(project: ModrinthSearchResult) {
  if (project.project_type === 'modpack') {
    modpackProject.value = project
    return
  }
  detailsProject.value = project
}

function ensureSignedIn(): boolean {
  if (authStore.authenticated) return true
  notifications.notifyWarning('Sign in first', 'Sign in with your Microsoft account to manage instances.')
  return false
}

function quickInstall(project: ModrinthSearchResult) {
  if (project.project_type === 'modpack') {
    if (ensureSignedIn()) modpackProject.value = project
    return
  }
  install(project, null, true)
}

function install(project: ModrinthSearchResult, versionId: string | null, deps: boolean) {
  if (!ensureSignedIn()) return
  if (!target.value) {
    pickerProject.value = { project, versionId, deps }
    return
  }
  installInto(target.value, project, versionId, deps)
}

function pickInstance(instance: Instance) {
  const pending = pickerProject.value
  if (!pending) return
  installInto(instance, pending.project, pending.versionId, pending.deps).finally(() => {
    pickerProject.value = null
  })
}

async function installInto(instance: Instance, project: ModrinthSearchResult, versionId: string | null, deps: boolean) {
  installingId.value = project.project_id
  try {
    const result = await addInstanceMod(instance.id, project.project_id, versionId, deps)
    const extra = result.installed.length - 1
    notifications.notifySuccess(
      `${project.title} installed`,
      extra > 0 ? `Added to ${instance.name} with ${extra} required dependenc${extra === 1 ? 'y' : 'ies'}.` : `Added to ${instance.name}.`
    )
    if (result.warnings.length > 0) notifications.notifyWarning('Heads up', result.warnings.slice(0, 3).join(' · '))
    if (target.value?.id === instance.id) {
      const next = new Set(installedIds.value)
      for (const m of result.installed) next.add(m.project_id)
      installedIds.value = next
    }
    detailsProject.value = null
  } catch (e) {
    notifications.notifyError(`Could not install ${project.title}`, String(e))
  } finally {
    installingId.value = null
  }
}

watch([projectType, targetId, compatibleOnly, sortIndex, gameVersion, loaderFilter, categories], () => {
  router.replace({ query: { ...route.query, type: projectType.value, instance: targetId.value || undefined } }).catch(() => void 0)
  runSearch()
})

watch(query, scheduleSearch)
watch(targetId, refreshInstalled)

onMounted(async () => {
  if (authStore.authenticated && !instanceStore.loaded) await instanceStore.load()
  if (targetId.value && !instanceStore.byId(targetId.value)) targetId.value = ''
  refreshInstalled()
  modrinthStore.loadAllTags().catch(() => void 0)
  runSearch()
})
</script>

<style scoped lang="scss">
.browse {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.browse__target {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  color: var(--accent);
}

.browse__target-label {
  font-family: var(--font-pixel);
  font-size: 0.55rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.browse__target-select {
  flex: 1;
  min-width: 220px;
  max-width: 460px;
  padding: 0.5rem 2.5rem 0.5rem 0.8rem;
}

.browse__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.browse__search {
  position: relative;
  flex: 1;
  min-width: 200px;
  color: var(--text-secondary);

  .app-icon {
    position: absolute;
    top: 50%;
    left: 0.85rem;
    transform: translateY(-50%);
  }

  .input {
    padding: 0.55rem 0.8rem 0.55rem 2.4rem;
  }
}

.browse__sort {
  width: auto;
  padding: 0.55rem 2.5rem 0.55rem 0.8rem;
}

.btn--active {
  border-color: var(--accent);
}

.browse__filters {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.browse__filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.browse__filter-row--wrap {
  align-items: flex-start;
}

.browse__filter-select {
  width: auto;
  min-width: 140px;
  padding: 0.45rem 2.4rem 0.45rem 0.7rem;
}

.browse__clear {
  align-self: flex-start;
}

.browse__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.browse__card {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  min-height: 190px;
  padding: 1rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);

  &:hover,
  &:focus-visible {
    outline: none;
    border-color: var(--accent);
    transform: translate(-2px, -2px);
    box-shadow: 4px 4px 0 0 var(--shadow-color);
  }
}

.browse__card--skeleton {
  cursor: default;
  background: linear-gradient(90deg, var(--bg-secondary), var(--bg-tertiary), var(--bg-secondary));
  background-size: 200% 100%;
  animation: browse-shimmer 1.2s linear infinite;

  &:hover {
    transform: none;
    box-shadow: none;
    border-color: var(--border-color);
  }
}

.browse__card-top {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.browse__card-icon {
  width: 48px;
  height: 48px;
  color: var(--text-secondary);
}

.browse__card-heading {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.browse__card-title {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 0.65rem;
  line-height: 1.3;
  text-transform: none;
}

.browse__card-author {
  font-size: 0.95rem;
  color: var(--text-secondary);
}

.browse__card-desc {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  line-height: 1.2;
  color: var(--text-secondary);
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.browse__card-footer {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: auto;
}

.browse__card-stat {
  display: inline-flex;
  white-space: nowrap;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.95rem;
  color: var(--text-secondary);
}

.browse__card-spacer {
  flex: 1;
}

.browse__more {
  display: flex;
  justify-content: center;
}

@keyframes browse-shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
