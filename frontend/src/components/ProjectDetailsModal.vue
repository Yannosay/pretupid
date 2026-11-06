<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal modal--wide" role="dialog" aria-modal="true">
      <div class="modal__header">
        <div class="details__heading">
          <div class="list__icon details__icon">
            <img v-if="project.icon_url" :src="project.icon_url" alt="" />
            <AppIcon v-else name="puzzle" :size="24" />
          </div>
          <div>
            <h3 class="modal__title">{{ project.title }}</h3>
            <p class="modal__subtitle">by {{ project.author }} · {{ formatCount(project.downloads) }} downloads</p>
          </div>
        </div>
        <button class="modal__close" aria-label="Close" @click="emit('close')">×</button>
      </div>

      <div class="modal__body">
        <p class="details__description">{{ project.description }}</p>
        <div class="chip-row">
          <span v-for="cat in project.display_categories" :key="cat" class="chip chip--static">{{ cat }}</span>
          <span v-if="project.client_side === 'required'" class="chip chip--static">Client required</span>
          <span v-if="project.server_side === 'required'" class="chip chip--static">Server required</span>
        </div>

        <div class="details__versions-header">
          <h4 class="form-label">Versions</h4>
          <label v-if="target" class="toggle">
            <input v-model="onlyCompatible" type="checkbox" class="toggle__input" />
            <span class="toggle__track" />
            <span class="toggle__label">Only for {{ target.name }}</span>
          </label>
        </div>

        <div v-if="loading" class="pixel-loader">
          Loading versions
          <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
        </div>
        <div v-else-if="error" class="form-error form-error--block">{{ error }}</div>
        <div v-else-if="shownVersions.length === 0" class="empty">
          <p class="empty__title">No matching versions</p>
          <p class="empty__hint">Nothing was published for this Minecraft version and loader yet.</p>
        </div>
        <ul v-else class="list details__versions">
          <li v-for="version in shownVersions" :key="version.id">
            <button
              class="list__item details__version"
              :class="{ 'details__version--selected': selectedId === version.id }"
              @click="selectedId = version.id"
            >
              <span class="badge" :class="badgeClass(version.version_type)">{{ version.version_type }}</span>
              <span class="list__body">
                <span class="details__version-name">{{ version.name }}</span>
                <span class="list__meta">
                  {{ summarize(version.game_versions) }} · {{ version.loaders.join(', ') }} · {{ formatRelative(version.date_published) }}
                </span>
              </span>
            </button>
          </li>
        </ul>
      </div>

      <div class="modal__footer">
        <label v-if="project.project_type === 'mod'" class="toggle">
          <input v-model="withDependencies" type="checkbox" class="toggle__input" />
          <span class="toggle__track" />
          <span class="toggle__label">Install required dependencies</span>
        </label>
        <span class="modal__footer-spacer" />
        <button class="btn btn--ghost" @click="emit('close')">Close</button>
        <button v-if="installed" class="btn btn--secondary" disabled>
          <AppIcon name="check" :size="14" />
          Installed
        </button>
        <button v-else class="btn" @click="emit('install', selectedId, withDependencies)">
          <AppIcon name="download" :size="14" />
          {{ selectedId ? 'Install selected version' : 'Install best match' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { getModrinthVersions } from '@/api'
import { instanceModLoaders } from '@/utils/compat'
import { formatCount, formatRelative } from '@/utils/format'
import type { Instance, ModrinthSearchResult, ModrinthVersion } from '@/types'

const props = defineProps<{ project: ModrinthSearchResult; target: Instance | null; installed: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'install', versionId: string | null, deps: boolean): void }>()

const versions = ref<ModrinthVersion[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedId = ref<string | null>(null)
const onlyCompatible = ref(true)
const withDependencies = ref(true)

const shownVersions = computed(() => {
  if (!props.target || !onlyCompatible.value) return versions.value.slice(0, 60)
  const target = props.target
  const loaders = instanceModLoaders(target)
  return versions.value
    .filter((v) => v.game_versions.includes(target.minecraft_version))
    .filter((v) => props.project.project_type !== 'mod' || v.loaders.some((l) => loaders.includes(l)))
    .slice(0, 60)
})

function summarize(list: string[]): string {
  if (list.length <= 3) return list.join(', ')
  return `${list[0]} – ${list[list.length - 1]}`
}

function badgeClass(type: string): string {
  if (type === 'release') return 'badge--success'
  if (type === 'beta') return 'badge--warning'
  return 'badge--danger'
}

onMounted(async () => {
  loading.value = true
  try {
    versions.value = await getModrinthVersions(props.project.project_id)
  } catch (e) {
    error.value = String(e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.details__heading {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  min-width: 0;
}

.details__icon {
  width: 52px;
  height: 52px;
  color: var(--text-secondary);
}

.details__description {
  margin: 0;
  line-height: 1.25;
}

.details__versions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;

  h4 {
    margin: 0;
  }
}

.details__versions {
  max-height: 40vh;
  overflow-y: auto;
}

.details__version {
  width: 100%;
  text-align: left;
  color: var(--text-primary);
  cursor: pointer;

  &:hover {
    border-color: var(--accent);
  }
}

.details__version--selected {
  border-color: var(--accent);
  box-shadow: inset 0 0 0 2px var(--accent);
}

.details__version-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 1.1rem;
}
</style>
