<template>
  <section class="settings-tab">
    <div class="settings-tab__grid">
      <div class="panel settings-tab__card">
        <h3 class="panel__title">General</h3>
        <div class="form-group">
          <label class="form-label" for="set-name">Name</label>
          <input id="set-name" v-model="general.name" class="input" maxlength="64" />
        </div>
        <div class="form-group">
          <label class="form-label" for="set-group">Group</label>
          <input id="set-group" v-model="general.group" class="input" maxlength="32" list="set-groups" placeholder="No group" />
          <datalist id="set-groups">
            <option v-for="g in instanceStore.groups" :key="g" :value="g" />
          </datalist>
        </div>
        <div class="form-group">
          <label class="form-label" for="set-notes">Notes</label>
          <input id="set-notes" v-model="general.description" class="input" maxlength="500" placeholder="Anything to remember" />
        </div>
      </div>

      <div class="panel settings-tab__card">
        <h3 class="panel__title">Memory</h3>
        <div class="settings-tab__memory">
          <span class="settings-tab__memory-value">{{ formatMemory(settings.memory_max_mb) }}</span>
          <span v-if="system" class="form-hint">
            of {{ formatMemory(system.total_memory_mb) }} ·
            <button class="settings-tab__link" @click="settings.memory_max_mb = system.recommended_memory_mb">
              use recommended {{ formatMemory(system.recommended_memory_mb) }}
            </button>
          </span>
        </div>
        <input
          v-model.number="settings.memory_max_mb"
          type="range"
          class="settings-tab__range"
          :min="1024"
          :max="memoryCeiling"
          step="256"
          aria-label="Maximum memory"
        />
        <p v-if="memoryWarning" class="form-hint settings-tab__warn">{{ memoryWarning }}</p>
        <p v-else class="form-hint">Most modpacks run well with 4–8 GB. Giving more than you need can cause stutters.</p>
      </div>

      <div class="panel settings-tab__card">
        <h3 class="panel__title">Window</h3>
        <div class="chip-row">
          <button
            v-for="preset in resolutionPresets"
            :key="preset.label"
            class="chip"
            :class="{ 'chip--active': settings.resolution_width === preset.w && settings.resolution_height === preset.h }"
            @click="setResolution(preset.w, preset.h)"
          >
            {{ preset.label }}
          </button>
        </div>
        <div class="settings-tab__pair">
          <div class="form-group">
            <label class="form-label" for="set-width">Width</label>
            <input id="set-width" v-model.number="settings.resolution_width" type="number" class="input" min="320" />
          </div>
          <div class="form-group">
            <label class="form-label" for="set-height">Height</label>
            <input id="set-height" v-model.number="settings.resolution_height" type="number" class="input" min="240" />
          </div>
        </div>
        <label class="toggle">
          <input v-model="settings.fullscreen" type="checkbox" class="toggle__input" />
          <span class="toggle__track" />
          <span class="toggle__label">Start in fullscreen</span>
        </label>
      </div>

      <div class="panel settings-tab__card">
        <h3 class="panel__title">Join a server on launch</h3>
        <div class="form-group">
          <label class="form-label" for="set-server">Server address</label>
          <input
            id="set-server"
            v-model="server"
            class="input"
            placeholder="play.example.net or 1.2.3.4:25565"
            maxlength="253"
          />
          <span class="form-hint">Leave empty to open the main menu as usual.</span>
        </div>
      </div>

      <div class="panel settings-tab__card settings-tab__card--wide">
        <h3 class="panel__title">Game version</h3>
        <p class="form-hint">
          Currently Minecraft {{ instance.minecraft_version }} with {{ loaderLabel(instance.mod_loader) }}
          <template v-if="instance.loader_version">{{ instance.loader_version }}</template>.
        </p>
        <details class="settings-tab__details" @toggle="onVersionToggle">
          <summary>Change version or loader</summary>
          <div class="settings-tab__version">
            <div class="form-group">
              <label class="form-label" for="set-mc">Minecraft</label>
              <select id="set-mc" v-model="version.mc" class="input">
                <option v-for="v in releaseVersions" :key="v.id" :value="v.id">{{ v.id }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label" for="set-loader">Loader</label>
              <select id="set-loader" v-model="version.loader" class="input">
                <option value="vanilla">Vanilla</option>
                <option value="fabric">Fabric</option>
                <option value="quilt">Quilt</option>
                <option value="forge">Forge</option>
                <option value="neoforge">NeoForge</option>
              </select>
            </div>
            <div v-if="version.loader !== 'vanilla'" class="form-group">
              <label class="form-label" for="set-loader-version">Loader version</label>
              <select id="set-loader-version" v-model="version.loaderVersion" class="input" :disabled="loadingLoaders">
                <option v-if="loadingLoaders" value="">Loading</option>
                <option v-else-if="loaderVersions.length === 0" value="">Not available</option>
                <option v-for="v in loaderVersions" :key="v.version" :value="v.version">
                  {{ v.version }}{{ v.recommended ? ' — recommended' : '' }}
                </option>
              </select>
            </div>
          </div>
          <p class="form-hint settings-tab__warn">Mods are not converted automatically. Use Check updates afterwards.</p>
          <button class="btn btn--secondary btn--small" :disabled="!versionChanged || savingVersion" @click="applyVersion">
            {{ savingVersion ? 'Applying' : 'Apply version change' }}
          </button>
        </details>
      </div>

      <div class="panel settings-tab__card settings-tab__card--wide">
        <h3 class="panel__title">Java &amp; advanced</h3>
        <div class="form-group">
          <label class="form-label" for="set-java">Java executable</label>
          <div class="settings-tab__row">
            <input id="set-java" v-model="javaPath" class="input" placeholder="Automatic (recommended)" />
            <button class="btn btn--secondary btn--small" @click="pickJava">Browse</button>
            <button v-if="javaPath" class="btn btn--ghost btn--small" @click="javaPath = ''">Reset</button>
          </div>
          <span class="form-hint">Automatic picks and downloads the right Java for this Minecraft version.</span>
        </div>
        <div class="settings-tab__pair">
          <div class="form-group">
            <label class="form-label" for="set-jvm">JVM arguments <span class="form-label-optional">(one per line)</span></label>
            <textarea id="set-jvm" v-model="jvmArgs" class="input settings-tab__mono" rows="3" spellcheck="false" />
          </div>
          <div class="form-group">
            <label class="form-label" for="set-game">Game arguments <span class="form-label-optional">(one per line)</span></label>
            <textarea id="set-game" v-model="gameArgs" class="input settings-tab__mono" rows="3" spellcheck="false" />
          </div>
        </div>
      </div>
    </div>

    <div class="settings-tab__savebar" :class="{ 'settings-tab__savebar--visible': dirty }">
      <span>You have unsaved changes</span>
      <button class="btn btn--ghost btn--small" :disabled="saving" @click="revert">Discard</button>
      <button class="btn btn--small" :disabled="saving" @click="save">{{ saving ? 'Saving' : 'Save changes' }}</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { open } from '@tauri-apps/plugin-dialog'
import { fetchVersions, getInstanceSettings, getLoaderVersions, getSystemInfo, saveInstanceSettings } from '@/api'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import { loaderLabel } from '@/utils/format'
import type { Instance, InstanceSettings, LoaderVersion, SystemInfo, VersionEntry } from '@/types'

const props = defineProps<{ instance: Instance }>()
const emit = defineEmits<{ (e: 'updated', instance: Instance): void }>()

const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()

const defaults: InstanceSettings = {
  memory_min_mb: 512,
  memory_max_mb: 4096,
  resolution_width: 1280,
  resolution_height: 720,
  fullscreen: false,
  java_path: null,
  extra_jvm_args: [],
  extra_game_args: [],
  quick_play_server: null
}

const settings = reactive<InstanceSettings>({ ...defaults })
const general = reactive({ name: '', group: '', description: '' })
const server = ref('')
const javaPath = ref('')
const jvmArgs = ref('')
const gameArgs = ref('')
const snapshot = ref('')
const system = ref<SystemInfo | null>(null)
const saving = ref(false)

const versions = ref<VersionEntry[]>([])
const version = reactive({ mc: '', loader: 'vanilla' as Instance['mod_loader'], loaderVersion: '' })
const loaderVersions = ref<LoaderVersion[]>([])
const loadingLoaders = ref(false)
const savingVersion = ref(false)

const resolutionPresets = [
  { label: '854 × 480', w: 854, h: 480 },
  { label: '1280 × 720', w: 1280, h: 720 },
  { label: '1600 × 900', w: 1600, h: 900 },
  { label: '1920 × 1080', w: 1920, h: 1080 }
]

const memoryCeiling = computed(() => {
  const total = system.value?.total_memory_mb ?? 16384
  return Math.max(2048, Math.floor((total - 1024) / 256) * 256)
})

const memoryWarning = computed(() => {
  const total = system.value?.total_memory_mb
  if (!total) return null
  if (settings.memory_max_mb > total * 0.75) return 'That is most of your RAM. Windows and other apps may slow down.'
  return null
})

const releaseVersions = computed(() => versions.value.filter((v) => v.type === 'release' || v.id === props.instance.minecraft_version))

const versionChanged = computed(
  () =>
    version.mc !== props.instance.minecraft_version ||
    version.loader !== props.instance.mod_loader ||
    (version.loader !== 'vanilla' && version.loaderVersion !== (props.instance.loader_version ?? ''))
)

function splitLines(value: string): string[] {
  return value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
}

function currentState(): string {
  return JSON.stringify({
    settings: { ...settings },
    general: { ...general },
    server: server.value.trim(),
    javaPath: javaPath.value.trim(),
    jvm: splitLines(jvmArgs.value),
    game: splitLines(gameArgs.value)
  })
}

const dirty = computed(() => snapshot.value !== '' && currentState() !== snapshot.value)

function formatMemory(mb: number): string {
  return mb >= 1024 ? `${(mb / 1024).toFixed(mb % 1024 === 0 ? 0 : 1)} GB` : `${mb} MB`
}

function setResolution(w: number, h: number) {
  settings.resolution_width = w
  settings.resolution_height = h
}

async function load() {
  const loaded = await getInstanceSettings(props.instance.id)
  Object.assign(settings, defaults, loaded)
  general.name = props.instance.name
  general.group = props.instance.group_name ?? ''
  general.description = props.instance.description
  server.value = loaded.quick_play_server ?? ''
  javaPath.value = loaded.java_path ?? ''
  jvmArgs.value = loaded.extra_jvm_args.join('\n')
  gameArgs.value = loaded.extra_game_args.join('\n')
  snapshot.value = currentState()
}

function revert() {
  load().catch(() => void 0)
}

async function save() {
  saving.value = true
  try {
    const payload: InstanceSettings = {
      ...settings,
      memory_min_mb: Math.min(1024, settings.memory_max_mb),
      java_path: javaPath.value.trim() || null,
      quick_play_server: server.value.trim() || null,
      extra_jvm_args: splitLines(jvmArgs.value),
      extra_game_args: splitLines(gameArgs.value)
    }
    await saveInstanceSettings(props.instance.id, payload)
    const generalChanged =
      general.name.trim() !== props.instance.name ||
      general.group.trim() !== (props.instance.group_name ?? '') ||
      general.description.trim() !== props.instance.description
    if (generalChanged) {
      const updated = await instanceStore.update(props.instance.id, {
        name: general.name.trim() || props.instance.name,
        group_name: general.group.trim(),
        description: general.description.trim()
      })
      if (updated) emit('updated', updated)
    }
    await load()
    notifications.notifySuccess('Saved', 'Instance settings were updated.')
  } catch (e) {
    notifications.notifyError('Could not save', String(e))
  } finally {
    saving.value = false
  }
}

async function pickJava() {
  try {
    const selected = await open({
      multiple: false,
      directory: false,
      filters: [{ name: 'Java', extensions: ['exe'] }]
    })
    if (typeof selected === 'string') javaPath.value = selected
  } catch (e) {
    notifications.notifyError('Could not open file picker', String(e))
  }
}

async function onVersionToggle(event: Event) {
  if (!(event.target as HTMLDetailsElement).open) return
  version.mc = props.instance.minecraft_version
  version.loader = props.instance.mod_loader === 'modrinth' ? 'fabric' : props.instance.mod_loader
  version.loaderVersion = props.instance.loader_version ?? ''
  if (versions.value.length === 0) {
    try {
      versions.value = await fetchVersions()
    } catch (e) {
      notifications.notifyError('Could not load versions', String(e))
    }
  }
  await refreshLoaders(false)
}

async function refreshLoaders(pickDefault: boolean) {
  loaderVersions.value = []
  if (version.loader === 'vanilla' || !version.mc) return
  loadingLoaders.value = true
  try {
    loaderVersions.value = await getLoaderVersions(version.loader, version.mc)
    const keep = loaderVersions.value.some((v) => v.version === version.loaderVersion)
    if (pickDefault || !keep) {
      version.loaderVersion = (loaderVersions.value.find((v) => v.recommended) ?? loaderVersions.value[0])?.version ?? ''
    }
  } catch (e) {
    notifications.notifyError('Could not load loader versions', String(e))
  } finally {
    loadingLoaders.value = false
  }
}

watch(
  () => [version.mc, version.loader],
  (_, previous) => {
    if (previous && previous[0] !== '') refreshLoaders(true)
  }
)

async function applyVersion() {
  if (version.loader !== 'vanilla' && !version.loaderVersion) {
    notifications.notifyWarning('Pick a loader version', 'This loader needs a specific version.')
    return
  }
  savingVersion.value = true
  try {
    const updated = await instanceStore.update(props.instance.id, {
      minecraft_version: version.mc,
      mod_loader: version.loader,
      loader_version: version.loader === 'vanilla' ? '' : version.loaderVersion
    })
    if (updated) emit('updated', updated)
    notifications.notifySuccess('Version changed', `Now using Minecraft ${version.mc} with ${loaderLabel(version.loader)}.`)
  } catch (e) {
    notifications.notifyError('Could not change version', String(e))
  } finally {
    savingVersion.value = false
  }
}

watch(() => props.instance.id, () => load().catch(() => void 0))

onMounted(async () => {
  await load().catch((e) => notifications.notifyError('Could not load settings', String(e)))
  try {
    system.value = await getSystemInfo()
  } catch {
    system.value = null
  }
})

defineExpose({ dirty })
</script>

<style scoped lang="scss">
.settings-tab {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-bottom: 4rem;
}

.settings-tab__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1rem;
}

.settings-tab__card {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;

  .panel__title {
    font-size: 0.85rem;
  }
}

.settings-tab__card--wide {
  grid-column: 1 / -1;
}

.settings-tab__memory {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.settings-tab__memory-value {
  font-family: var(--font-pixel);
  font-size: 1rem;
  color: var(--accent);
}

.settings-tab__link {
  padding: 0;
  color: var(--accent);
  font: inherit;
  text-decoration: underline;
  background: none;
  border: none;
  cursor: pointer;
}

.settings-tab__range {
  width: 100%;
  accent-color: var(--accent);
  cursor: pointer;
}

.settings-tab__warn {
  color: var(--warning);
}

.settings-tab__pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
}

.settings-tab__row {
  display: flex;
  gap: 0.5rem;

  .input {
    flex: 1;
  }
}

.settings-tab__mono {
  font-family: var(--font-mono);
  font-size: 0.9rem;
}

.settings-tab__details {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;

  summary {
    cursor: pointer;
    font-family: var(--font-pixel);
    font-size: 0.85rem;
    color: var(--accent);
  }

  &[open] summary {
    margin-bottom: 0.75rem;
  }

  .btn {
    margin-top: 0.5rem;
  }
}

.settings-tab__version {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
}

.settings-tab__savebar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--accent);
  border-radius: var(--edge-radius);
  box-shadow: 0 6px 18px var(--shadow-color);
  transform: translateY(150%);
  opacity: 0;
  pointer-events: none;
  transition: transform 0.18s ease, opacity 0.18s ease;

  span {
    flex: 1;
  }
}

.settings-tab__savebar--visible {
  transform: none;
  opacity: 1;
  pointer-events: auto;
}
</style>
