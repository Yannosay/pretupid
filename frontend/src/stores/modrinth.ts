import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api'
import type {
  ModrinthSearchResponse,
  ModrinthProject,
  ModrinthVersion,
  ModrinthCategory,
  ModrinthLoader,
  ModrinthGameVersion
} from '@/types'

export const useModrinthStore = defineStore('modrinth', () => {
  const searchResults = ref<ModrinthSearchResponse | null>(null)
  const categories = ref<ModrinthCategory[]>([])
  const loaders = ref<ModrinthLoader[]>([])
  const gameVersions = ref<ModrinthGameVersion[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentQuery = ref('')
  const currentFacets = ref<string | undefined>(undefined)
  const currentOffset = ref(0)
  const currentLimit = ref(20)
  const currentIndex = ref<string | null>(null)
  let searchToken = 0
  const totalHits = ref(0)
  const activeProject = ref<ModrinthProject | null>(null)
  const activeVersions = ref<ModrinthVersion[]>([])
  const selectedVersion = ref<ModrinthVersion | null>(null)

  const hasMore = computed(() => {
    if (!searchResults.value) return false
    return searchResults.value.hits.length < totalHits.value
  })

  async function loadCategories() {
    try {
      categories.value = await api.getModrinthCategories()
    } catch (e) {
      error.value = String(e)
    }
  }

  async function loadLoaders() {
    try {
      loaders.value = await api.getModrinthLoaders()
    } catch (e) {
      error.value = String(e)
    }
  }

  async function loadGameVersions() {
    try {
      gameVersions.value = await api.getModrinthGameVersions()
    } catch (e) {
      error.value = String(e)
    }
  }

  async function loadAllTags() {
    await Promise.all([
      loadCategories(),
      loadLoaders(),
      loadGameVersions()
    ])
  }

  async function search(query?: string, facets?: string | null, offset?: number, limit?: number, index?: string | null) {
    const token = ++searchToken
    loading.value = true
    error.value = null
    try {
      const q = query ?? currentQuery.value
      const f = facets ?? currentFacets.value ?? undefined
      const o = offset ?? 0
      const l = limit ?? currentLimit.value
      const i = index === undefined ? currentIndex.value : index

      const response = await api.searchModrinth(q, f, i, o, l)
      if (token !== searchToken) return
      searchResults.value = response
      totalHits.value = response.total_hits
      currentQuery.value = q
      currentFacets.value = f ?? undefined
      currentOffset.value = o
      currentLimit.value = l
      currentIndex.value = i
    } catch (e) {
      if (token === searchToken) error.value = String(e)
    } finally {
      if (token === searchToken) loading.value = false
    }
  }

  async function loadMore() {
    if (!hasMore.value || loading.value) return
    const nextOffset = currentOffset.value + currentLimit.value
    loading.value = true
    try {
      const response = await api.searchModrinth(
        currentQuery.value,
        currentFacets.value ?? undefined,
        currentIndex.value,
        nextOffset,
        currentLimit.value
      )
      if (searchResults.value) {
        searchResults.value.hits.push(...response.hits)
        searchResults.value.offset = response.offset
        searchResults.value.limit = response.limit
        searchResults.value.total_hits = response.total_hits
      }
      currentOffset.value = nextOffset
      totalHits.value = response.total_hits
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function loadProject(idOrSlug: string) {
    loading.value = true
    error.value = null
    try {
      activeProject.value = await api.getModrinthProject(idOrSlug)
      activeVersions.value = await api.getModrinthVersions(idOrSlug)
      selectedVersion.value = null
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  function clearProject() {
    activeProject.value = null
    activeVersions.value = []
    selectedVersion.value = null
  }

  function selectVersion(version: ModrinthVersion) {
    selectedVersion.value = version
  }

  return {
    searchResults,
    categories,
    loaders,
    gameVersions,
    loading,
    error,
    currentQuery,
    currentFacets,
    currentOffset,
    currentLimit,
    totalHits,
    hasMore,
    activeProject,
    activeVersions,
    selectedVersion,
    loadCategories,
    loadLoaders,
    loadGameVersions,
    loadAllTags,
    search,
    loadMore,
    loadProject,
    clearProject,
    selectVersion
  }
})