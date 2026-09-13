<template>
  <div class="modrinth">
    <header class="modrinth__header">
      <h1 class="modrinth__title">Modrinth Library</h1>
      <div class="modrinth__search">
        <input
          v-model="searchInput"
          type="text"
          class="input"
          placeholder="Search Modrinth..."
          @keyup.enter="handleSearch"
        />
        <button class="btn" @click="handleSearch">Search</button>
      </div>
    </header>

    <div v-if="installError" class="modrinth__error">{{ installError }}</div>
    <div v-if="installSuccess" class="modrinth__success">{{ installSuccess }}</div>

    <div class="modrinth__filters">
      <div class="modrinth__filter-group">
        <span class="modrinth__filter-label">Project Type</span>
        <div class="modrinth__filter-buttons">
          <button
            class="btn btn--ghost btn--small"
            :class="{ 'btn--active': currentProjectType === 'mod' }"
            @click="setProjectType('mod')"
          >
            Mods
          </button>
          <button
            class="btn btn--ghost btn--small"
            :class="{ 'btn--active': currentProjectType === 'modpack' }"
            @click="setProjectType('modpack')"
          >
            Modpacks
          </button>
        </div>
      </div>

      <div class="modrinth__filter-group">
        <span class="modrinth__filter-label">Categories</span>
        <div class="modrinth__filter-buttons">
          <button
            v-for="cat in modrinthStore.categories.filter((c) => c.project_type === currentProjectType)"
            :key="cat.name"
            class="btn btn--ghost btn--small"
            :class="{ 'btn--active': selectedCategories.includes(cat.name) }"
            @click="toggleCategory(cat.name)"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>

      <div class="modrinth__filter-group">
        <span class="modrinth__filter-label">Loaders</span>
        <div class="modrinth__filter-buttons">
          <button
            v-for="loader in modrinthStore.loaders"
            :key="loader.name"
            class="btn btn--ghost btn--small"
            :class="{ 'btn--active': selectedLoaders.includes(loader.name) }"
            @click="toggleLoader(loader.name)"
          >
            {{ loader.name }}
          </button>
        </div>
      </div>

      <div ref="gameVersionsDropdown" class="modrinth__filter-group modrinth__filter-group--game-versions">
        <span class="modrinth__filter-label">Game Versions</span>
        <div class="dropdown" :class="{ 'dropdown--open': gameVersionsOpen }">
          <button class="btn btn--ghost btn--small dropdown__trigger" @click.stop="gameVersionsOpen = !gameVersionsOpen">
            <span>{{ selectedGameVersions.length ? selectedGameVersions.join(', ') : 'All Versions' }}</span>
            <span class="dropdown__chevron">▾</span>
          </button>
          <div v-if="gameVersionsOpen" class="dropdown__panel" @click.stop>
            <label class="dropdown__snapshot-toggle">
              <input :checked="showSnapshots" type="checkbox" class="dropdown__checkbox" @change="toggleShowSnapshots" />
              <span>Show non-release versions</span>
            </label>
            <div class="dropdown__version-list">
              <button
                v-for="gv in filteredGameVersions"
                :key="gv.version"
                class="dropdown__version-item"
                :class="{ 'dropdown__version-item--selected': selectedGameVersions.includes(gv.version) }"
                @click.stop="toggleGameVersion(gv.version)"
              >
                {{ gv.version }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="modrinthStore.loading" class="modrinth__loading">
      <span class="pixel-loader">
        Loading
        <span class="pixel-loader__dots">
          <span class="pixel-loader__dot"></span>
          <span class="pixel-loader__dot"></span>
          <span class="pixel-loader__dot"></span>
        </span>
      </span>
    </div>
    <div v-else-if="modrinthStore.error" class="modrinth__error">{{ modrinthStore.error }}</div>
    <div v-else-if="modrinthStore.searchResults?.hits.length === 0" class="modrinth__empty">No results found.</div>
    <div v-else class="modrinth__grid">
      <div
        v-for="project in modrinthStore.searchResults?.hits"
        :key="project.project_id"
        class="project-card"
        @click="openProjectOrAdd(project)"
      >
        <div class="project-card__icon">
          <img v-if="project.icon_url" :src="project.icon_url" :alt="project.title" class="project-card__image" />
          <span v-else class="project-card__placeholder">?</span>
        </div>
        <div class="project-card__info">
          <h3 class="project-card__title">{{ project.title }}</h3>
          <p class="project-card__author">by {{ project.author }}</p>
          <p class="project-card__description">{{ project.description }}</p>
          <div class="project-card__meta">
            <span class="project-card__downloads">{{ project.downloads }} downloads</span>
            <span class="project-card__categories">{{ project.display_categories.join(', ') }}</span>
          </div>
          <button class="btn btn--small project-card__add" @click.stop="openProjectOrAdd(project)">
            {{ project.project_type === 'modpack' ? 'Create Instance' : 'Add to Instance' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="modrinthStore.hasMore" class="modrinth__load-more">
      <button class="btn" :disabled="modrinthStore.loading" @click="loadMore">Load More</button>
    </div>

    <div v-if="modrinthStore.activeProject" class="project-modal">
      <div class="project-modal__overlay" @click="modrinthStore.clearProject"></div>
      <div class="project-modal__content">
        <button class="project-modal__close" @click="modrinthStore.clearProject">×</button>
        <div class="project-modal__header">
          <img
            v-if="modrinthStore.activeProject.icon_url"
            :src="modrinthStore.activeProject.icon_url"
            :alt="modrinthStore.activeProject.title"
            class="project-modal__image"
          />
          <h2 class="project-modal__title">{{ modrinthStore.activeProject.title }}</h2>
        </div>
        <p class="project-modal__description">{{ modrinthStore.activeProject.description }}</p>
        <div class="project-modal__versions">
          <h3>Versions</h3>
          <div v-if="modrinthStore.loading" class="pixel-loader">
            Loading Versions
            <span class="pixel-loader__dots">
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
            </span>
          </div>
          <div class="project-modal__version-list">
            <button
              v-for="version in modrinthStore.activeVersions"
              :key="version.id"
              class="project-modal__version"
              :class="{ 'project-modal__version--selected': modrinthStore.selectedVersion?.id === version.id }"
              @click="modrinthStore.selectVersion(version)"
            >
              <span class="project-modal__version-name">{{ version.name }}</span>
              <span class="project-modal__version-meta">
                {{ version.game_versions.join(', ') }} · {{ version.loaders.join(', ') }}
              </span>
            </button>
          </div>
        </div>
        <div class="project-modal__actions">
          <button
            class="btn"
            :disabled="!modrinthStore.selectedVersion || installing"
            @click="installSelected"
          >
            {{ installing ? 'Installing...' : 'Install' }}
          </button>
        </div>
      </div>
    </div>

    <AddToInstanceModal
      v-if="showAddToInstance && selectedProject"
      :project="selectedProject"
      :selected-version="selectedVersion"
      :error="installError"
      :installing="installing"
      @close="closeAddToInstance"
      @select="handleInstanceSelect"
    />
    <CreateModpackInstanceModal
      v-if="showModpackModal && selectedModpack"
      :project="selectedModpack"
      @close="showModpackModal = false"
      @created="showModpackModal = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import AddToInstanceModal from '@/components/AddToInstanceModal.vue'
import CreateModpackInstanceModal from '@/components/CreateModpackInstanceModal.vue'
import { useModrinthStore } from '@/stores/modrinth'
import { useSettingsStore } from '@/stores/settings'
import { useNotificationsStore } from '@/stores/notifications'
import { addInstanceMod, saveGlobalSettings } from '@/api'
import type { ModrinthSearchResult, ModrinthVersion } from '@/types'

const modrinthStore = useModrinthStore()
const settingsStore = useSettingsStore()
const notifications = useNotificationsStore()

const searchInput = ref('')
const currentProjectType = ref<'mod' | 'modpack'>('mod')
const selectedCategories = ref<string[]>([])
const selectedLoaders = ref<string[]>([])
const selectedGameVersions = ref<string[]>([])
const gameVersionsOpen = ref(false)
const gameVersionsDropdown = ref<HTMLElement | null>(null)
const showAddToInstance = ref(false)
const showModpackModal = ref(false)
const selectedProject = ref<ModrinthSearchResult | null>(null)
const selectedVersion = ref<ModrinthVersion | null>(null)
const selectedModpack = ref<ModrinthSearchResult | null>(null)
const installError = ref<string | null>(null)
const installSuccess = ref<string | null>(null)
const installing = ref(false)

const showSnapshots = computed(() => settingsStore.settings.globalSettings.show_snapshots)

const filteredGameVersions = computed(() => {
  if (showSnapshots.value) return modrinthStore.gameVersions
  return modrinthStore.gameVersions.filter((v) => v.version_type === 'release')
})

async function toggleShowSnapshots(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  const next = {
    ...settingsStore.settings.globalSettings,
    show_snapshots: checked
  }
  settingsStore.setGlobalSettings(next)

  try {
    await saveGlobalSettings(next)
  } catch (e) {
    console.error(e)
  }
}

function handleOutsideClick(event: MouseEvent) {
  if (
    gameVersionsOpen.value &&
    gameVersionsDropdown.value &&
    !gameVersionsDropdown.value.contains(event.target as Node)
  ) {
    gameVersionsOpen.value = false
  }
}

function buildFacets(): string {
  const groups: string[][] = []
  groups.push([`project_type:${currentProjectType.value}`])
  if (selectedCategories.value.length > 0)
    groups.push(selectedCategories.value.map((c) => `categories:${c}`))
  if (selectedLoaders.value.length > 0)
    groups.push(selectedLoaders.value.map((l) => `loaders:${l}`))
  if (selectedGameVersions.value.length > 0)
    groups.push(selectedGameVersions.value.map((v) => `versions:${v}`))
  return JSON.stringify(groups)
}

function performSearch() {
  modrinthStore.search(searchInput.value || '', buildFacets(), 0, 20)
}

function handleSearch() {
  performSearch()
}

function setProjectType(type: 'mod' | 'modpack') {
  currentProjectType.value = type
  selectedCategories.value = []
  performSearch()
}

function toggleCategory(cat: string) {
  const idx = selectedCategories.value.indexOf(cat)
  if (idx >= 0) selectedCategories.value.splice(idx, 1)
  else selectedCategories.value.push(cat)
  performSearch()
}

function toggleLoader(loader: string) {
  const idx = selectedLoaders.value.indexOf(loader)
  if (idx >= 0) selectedLoaders.value.splice(idx, 1)
  else selectedLoaders.value.push(loader)
  performSearch()
}

function toggleGameVersion(version: string) {
  const idx = selectedGameVersions.value.indexOf(version)
  if (idx >= 0) selectedGameVersions.value.splice(idx, 1)
  else selectedGameVersions.value.push(version)
  performSearch()
}

function loadMore() {
  modrinthStore.loadMore()
}

function openProjectOrAdd(project: ModrinthSearchResult) {
  if (project.project_type === 'modpack') {
    selectedModpack.value = project
    showModpackModal.value = true
  } else {
    selectedProject.value = project
    selectedVersion.value = null
    modrinthStore.loadProject(project.project_id)
  }
}

function installSelected() {
  if (modrinthStore.selectedVersion && selectedProject.value) {
    selectedVersion.value = modrinthStore.selectedVersion
    showAddToInstance.value = true
  }
}

function closeAddToInstance() {
  showAddToInstance.value = false
  selectedProject.value = null
  selectedVersion.value = null
  installError.value = null
  installSuccess.value = null
}

async function handleInstanceSelect(instanceId: string) {
  const project = selectedProject.value
  const version = selectedVersion.value
  if (!project || installing.value) return

  installing.value = true
  installError.value = null
  installSuccess.value = null

  try {
    if (version) {
      const file = version.files.find((f) => f.primary) || version.files[0]
      if (!file) {
        installError.value = 'Selected version has no downloadable file.'
        return
      }
      await addInstanceMod(
        instanceId,
        project.project_id,
        project.project_type,
        project.title,
        project.icon_url,
        version.id,
        version.name,
        file.url,
        file.filename
      )
    } else {
      await addInstanceMod(
        instanceId,
        project.project_id,
        project.project_type,
        project.title,
        project.icon_url,
        null,
        null,
        null,
        null
      )
    }

    installSuccess.value = 'Mod added successfully.'
    notifications.notifySuccess('Mod installed', `${project.title} was added to the instance.`)
    closeAddToInstance()
    setTimeout(() => {
      installSuccess.value = null
    }, 3000)
  } catch (e) {
    installError.value = String(e)
    notifications.notifyError('Failed to add mod', String(e))
  } finally {
    installing.value = false
  }
}

onMounted(async () => {
  await modrinthStore.loadAllTags()
  performSearch()
  document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleOutsideClick)
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.modrinth {
  padding: 0;
}
.modrinth__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  gap: 1rem;
  flex-wrap: wrap;
}
.modrinth__title {
  font-size: 2rem;
  margin: 0;
}
.modrinth__search {
  display: flex;
  gap: 0.5rem;
  flex: 1;
  max-width: 500px;
}
.modrinth__search .input {
  flex: 1;
}
.modrinth__filters {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 2rem;
}
.modrinth__filter-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.modrinth__filter-label {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}
.modrinth__filter-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.btn--active {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}
.modrinth__loading,
.modrinth__empty,
.modrinth__error {
  padding: 3rem;
  text-align: center;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  margin-bottom: 2rem;
}
.modrinth__error {
  color: #e74c3c;
}
.modrinth__success {
  padding: 1rem;
  margin-bottom: 1.5rem;
  text-align: center;
  background-color: #2ecc71;
  color: white;
  border-radius: var(--edge-radius);
  border: 2px solid #27ae60;
}
.modrinth__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}
.project-card {
  display: flex;
  gap: 1rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1rem;
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);
}
.project-card:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}
.project-card__icon {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-tertiary);
  border-radius: var(--edge-radius);
  overflow: hidden;
}
.project-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.project-card__placeholder {
  font-size: 1.5rem;
  color: var(--text-secondary);
}
.project-card__info {
  flex: 1;
  min-width: 0;
}
.project-card__title {
  font-size: 1.1rem;
  margin: 0 0 0.25rem;
}
.project-card__author {
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin: 0 0 0.5rem;
}
.project-card__description {
  font-size: 0.9rem;
  color: var(--text-primary);
  margin: 0 0 0.75rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.project-card__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8rem;
  color: var(--text-secondary);
  margin-bottom: 0.5rem;
}
.project-card__categories {
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  max-width: 50%;
}
.project-card__add {
  width: 100%;
  margin-top: 0.5rem;
}
.modrinth__load-more {
  display: flex;
  justify-content: center;
  margin-top: 2rem;
}
.project-modal {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.project-modal__overlay {
  position: absolute;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.6);
}
.project-modal__content {
  position: relative;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
  width: 90%;
  max-width: 700px;
  height: 80vh;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 1;
}
.project-modal__close {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  font-size: 2rem;
  line-height: 1;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
}
.project-modal__header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
  flex-shrink: 0;
}
.project-modal__image {
  width: 48px;
  height: 48px;
  border-radius: var(--edge-radius);
  object-fit: cover;
}
.project-modal__title {
  font-size: 1.5rem;
  margin: 0;
}
.project-modal__description {
  color: var(--text-secondary);
  margin-bottom: 1rem;
  flex-shrink: 0;
}
.project-modal__versions {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  margin-bottom: 0.5rem;
}
.project-modal__versions h3 {
  font-size: 1.2rem;
  margin-bottom: 0.5rem;
  flex-shrink: 0;
}
.project-modal__version-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-right: 0.25rem;
}
.project-modal__version {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  background-color: var(--bg-tertiary);
  cursor: pointer;
  transition: border-color var(--transition-fast);
  flex-shrink: 0;
}
.project-modal__version:hover {
  border-color: var(--accent);
}
.project-modal__version--selected {
  border-color: var(--accent);
  background-color: rgba(0, 0, 0, 0.1);
}
.project-modal__version-name {
  font-weight: bold;
}
.project-modal__version-meta {
  font-size: 0.8rem;
  color: var(--text-secondary);
}
.project-modal__actions {
  flex-shrink: 0;
  margin-top: auto;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
}
.dropdown {
  position: relative;
  display: inline-block;
}
.dropdown__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  min-width: 180px;
  max-width: 260px;
}
.dropdown__chevron {
  font-size: 0.7rem;
}
.dropdown__panel {
  position: absolute;
  top: calc(100% + 0.5rem);
  left: 0;
  min-width: 240px;
  max-height: 320px;
  overflow-y: auto;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.5rem;
  z-index: 100;
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}
.dropdown__snapshot-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  cursor: pointer;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 0.5rem;
}
.dropdown__checkbox {
  appearance: none;
  -webkit-appearance: none;
  width: 1rem;
  height: 1rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  position: relative;
  display: inline-block;
  flex-shrink: 0;
}
.dropdown__checkbox:checked {
  background-color: var(--accent);
  border-color: var(--accent);
}
.dropdown__checkbox:checked::after {
  content: '';
  position: absolute;
  left: 0.25rem;
  top: 0.05rem;
  width: 0.3rem;
  height: 0.55rem;
  border: solid var(--accent-text);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.dropdown__version-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 220px;
  overflow-y: auto;
}
.dropdown__version-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.4rem 0.5rem;
  background: none;
  border: 1px solid transparent;
  border-radius: var(--edge-radius);
  cursor: pointer;
  color: var(--text-primary);
  font-size: 0.75rem;
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
}
.dropdown__version-item:hover {
  background-color: var(--bg-tertiary);
  border-color: var(--border-color);
}
.dropdown__version-item--selected {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}
</style>