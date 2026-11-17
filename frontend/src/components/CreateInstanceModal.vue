<template>
  <div class="modal-overlay" v-backdrop-close="handleClose">
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="create-instance-title">
      <div class="modal__header">
        <div>
          <h3 id="create-instance-title" class="modal__title">New instance</h3>
          <p class="modal__subtitle">An instance is a separate Minecraft setup with its own mods, worlds and settings.</p>
        </div>
        <button class="modal__close" aria-label="Close" @click="handleClose">×</button>
      </div>

      <div class="modal__body">
        <div class="segmented create__mode">
          <button
            class="segmented__option"
            :class="{ 'segmented__option--active': mode === 'new' }"
            @click="mode = 'new'"
          >
            <AppIcon name="plus" :size="12" />
            Start fresh
          </button>
          <button
            class="segmented__option"
            :class="{ 'segmented__option--active': mode === 'import' }"
            @click="mode = 'import'"
          >
            <AppIcon name="upload" :size="12" />
            Import modpack file
          </button>
        </div>

        <template v-if="mode === 'new'">
          <div class="create__preview">
            <button class="create__preview-icon" title="Try another icon" aria-label="Try another icon" :disabled="creating" @click="shuffleIcon">
              <InstanceIcon :instance="previewInstance" :size="64" />
              <span class="create__preview-shuffle"><AppIcon name="refresh" :size="12" /></span>
            </button>
            <div class="create__preview-text">
              <input
                id="instance-name"
                v-model="name"
                type="text"
                class="create__name"
                maxlength="64"
                :placeholder="suggestedName"
                aria-label="Instance name"
                :disabled="creating"
                @keyup.enter="submit"
              />
              <span class="create__summary">
                {{ summary }}
              </span>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label" for="instance-version">Minecraft version</label>
            <div class="create__row">
              <select id="instance-version" v-model="minecraftVersion" class="input" :disabled="creating || loadingVersions">
                <option v-if="loadingVersions" value="">Loading versions</option>
                <option v-for="version in filteredVersions" :key="version.id" :value="version.id">
                  {{ version.id }}{{ version.type !== 'release' ? ` (${version.type})` : '' }}
                </option>
              </select>
              <label class="toggle create__snapshots">
                <input v-model="showSnapshots" type="checkbox" class="toggle__input" />
                <span class="toggle__track" />
                <span class="toggle__label">Snapshots</span>
              </label>
            </div>
            <span v-if="versionsError" class="form-error">{{ versionsError }}</span>
          </div>

          <div class="form-group">
            <span class="form-label">Mod loader</span>
            <div class="option-cards">
              <button
                v-for="option in loaderOptions"
                :key="option.value"
                class="option-card"
                :class="{ 'option-card--active': loader === option.value }"
                :disabled="creating"
                @click="loader = option.value"
              >
                <img v-if="blockTexture(option.block)" class="create__loader-art" :src="blockTexture(option.block) ?? undefined" alt="" />
                <span class="option-card__title">{{ option.label }}</span>
                <span class="option-card__hint">{{ option.hint }}</span>
              </button>
            </div>
          </div>

          <div v-if="loader !== 'vanilla'" class="form-group">
            <label class="form-label" for="loader-version">{{ loaderName }} version</label>
            <select
              id="loader-version"
              v-model="loaderVersion"
              class="input"
              :disabled="creating || loadingLoaderVersions || loaderVersions.length === 0"
            >
              <option v-if="loadingLoaderVersions" value="">Loading {{ loaderName }} versions</option>
              <option v-else-if="loaderVersions.length === 0" value="">No {{ loaderName }} build for {{ minecraftVersion }}</option>
              <option v-for="v in loaderVersions" :key="v.version" :value="v.version">
                {{ v.version }}{{ v.recommended ? ' — recommended' : v.latest ? ' — latest' : '' }}{{ v.stable ? '' : ' (beta)' }}
              </option>
            </select>
            <span class="form-hint">{{ loaderHint }}</span>
          </div>

          <PretupidClientOffer v-if="clientOffered" v-model="withClient" :disabled="creating" />

          <label v-if="loader === 'fabric' || loader === 'quilt'" class="toggle create__client">
            <input v-model="withPerformance" type="checkbox" class="toggle__input" :disabled="creating" />
            <span class="create__client-text">
              <strong>Install performance mods</strong>
              <span class="form-hint">Sodium, Lithium, FerriteCore, ImmediatelyFast and Entity Culling. Often doubles your FPS.</span>
            </span>
            <span class="toggle__track" />
          </label>

          <details class="create__advanced">
            <summary>More options</summary>
            <div class="create__advanced-body">
              <div class="form-group">
                <label class="form-label" for="instance-group">Group <span class="form-label-optional">(optional)</span></label>
                <input id="instance-group" v-model="group" list="instance-groups" type="text" class="input" maxlength="32" placeholder="e.g. Survival" />
                <datalist id="instance-groups">
                  <option v-for="g in instanceStore.groups" :key="g" :value="g" />
                </datalist>
              </div>
              <div class="form-group">
                <label class="form-label" for="instance-description">Notes <span class="form-label-optional">(optional)</span></label>
                <input id="instance-description" v-model="description" type="text" class="input" maxlength="500" placeholder="What is this instance for?" />
              </div>
            </div>
          </details>
        </template>

        <template v-else>
          <div class="create__import">
            <AppIcon name="box" :size="28" />
            <p class="create__import-text">
              Pick a <strong>.mrpack</strong> file exported from Pretupid, Modrinth App or Prism. Mods are downloaded and
              verified automatically.
            </p>
            <button class="btn btn--secondary" :disabled="creating" @click="pickModpack">
              <AppIcon name="folder" :size="14" />
              {{ modpackPath ? 'Choose another file' : 'Choose file' }}
            </button>
            <span v-if="modpackPath" class="create__import-file">{{ modpackFileName }}</span>
          </div>
          <div class="form-group">
            <label class="form-label" for="import-name">Name <span class="form-label-optional">(optional)</span></label>
            <input id="import-name" v-model="importName" type="text" class="input" maxlength="64" placeholder="Use the modpack's name" />
          </div>
        </template>

        <div v-if="submitError" class="form-error form-error--block">{{ submitError }}</div>
      </div>

      <div class="modal__footer">
        <button class="btn btn--ghost" :disabled="creating" @click="handleClose">Cancel</button>
        <button class="btn" :disabled="!canSubmit" @click="submit">
          {{ creating ? 'Creating' : mode === 'new' ? 'Create instance' : 'Import' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { vBackdropClose } from '@ui/backdropClose'
import { useEscapeKey } from '@/composables/useEscapeKey'
import { ref, computed, onMounted, watch } from 'vue'
import { open } from '@tauri-apps/plugin-dialog'
import AppIcon from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import PretupidClientOffer from '@/components/instance/PretupidClientOffer.vue'
import { BLOCK_MATERIALS, blockTexture } from '@/utils/instanceIcon'
import { fetchVersions, getLoaderVersions, importModpackFile, pretupidClientAvailable } from '@/api'
import { useInstanceStore } from '@/stores/instances'
import { useSettingsStore } from '@/stores/settings'
import { useNotificationsStore } from '@/stores/notifications'
import { useInstallsStore } from '@/stores/installs'
import { useSetupStore } from '@/stores/setup'
import { loaderLabel } from '@/utils/format'
import type { VersionEntry, LoaderVersion, Instance } from '@/types'

type LoaderChoice = 'vanilla' | 'fabric' | 'quilt' | 'forge' | 'neoforge'

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', instance: Instance): void
}>()

const instanceStore = useInstanceStore()
const settingsStore = useSettingsStore()
const notifications = useNotificationsStore()
const installs = useInstallsStore()
const setup = useSetupStore()

const mode = ref<'new' | 'import'>('new')
const versions = ref<VersionEntry[]>([])
const loadingVersions = ref(false)
const versionsError = ref<string | null>(null)
const showSnapshots = ref(settingsStore.settings.globalSettings.show_snapshots)

const name = ref('')
const description = ref('')
const group = ref('')
const minecraftVersion = ref('')
const loader = ref<LoaderChoice>('fabric')
const loaderVersion = ref('')
const loaderVersions = ref<LoaderVersion[]>([])
const loadingLoaderVersions = ref(false)
const withClient = ref(true)
const withPerformance = ref(true)
const clientAvailability = ref<Record<string, boolean>>({})
let loaderRequest = 0

const modpackPath = ref<string | null>(null)
const importName = ref('')

const creating = ref(false)
const submitError = ref<string | null>(null)

const loaderOptions: { value: LoaderChoice; label: string; hint: string; block: string }[] = [
  { value: 'vanilla', label: 'Vanilla', hint: 'Plain Minecraft, no mods', block: 'grass' },
  { value: 'fabric', label: 'Fabric', hint: 'Fast, most popular for mods', block: 'planks' },
  { value: 'quilt', label: 'Quilt', hint: 'Runs Fabric mods too', block: 'amethyst' },
  { value: 'forge', label: 'Forge', hint: 'Classic big modpacks', block: 'stone' },
  { value: 'neoforge', label: 'NeoForge', hint: 'Modern Forge successor', block: 'gold' }
]

const summary = computed(() => {
  const loaderPart = loader.value !== 'vanilla' && loaderVersion.value ? `${loaderName.value} ${loaderVersion.value}` : loaderName.value
  return `Minecraft ${minecraftVersion.value || '…'} · ${loaderPart}`
})

const ICON_CHOICES = Object.keys(BLOCK_MATERIALS)
const previewSeed = crypto.randomUUID()
const iconMaterial = ref(ICON_CHOICES[Math.floor(Math.random() * ICON_CHOICES.length)])

const previewInstance = computed(() => ({
  id: previewSeed,
  name: name.value.trim() || suggestedName.value,
  icon_url: null,
  icon_style: `block:${iconMaterial.value}`
}))

function shuffleIcon() {
  const index = ICON_CHOICES.indexOf(iconMaterial.value)
  iconMaterial.value = ICON_CHOICES[(index + 1) % ICON_CHOICES.length]
}

const filteredVersions = computed(() => {
  if (showSnapshots.value) return versions.value
  return versions.value.filter((v) => v.type === 'release')
})

const loaderName = computed(() => loaderLabel(loader.value))

const supportsMods = computed(() => loader.value === 'fabric' || loader.value === 'quilt')
const clientOffered = computed(() => supportsMods.value && clientAvailability.value[minecraftVersion.value] === true)

async function refreshClientAvailability(version: string) {
  if (!version || version in clientAvailability.value) return
  try {
    const available = await pretupidClientAvailable(version)
    clientAvailability.value = { ...clientAvailability.value, [version]: available }
  } catch {
    clientAvailability.value = { ...clientAvailability.value, [version]: false }
  }
}

const suggestedName = computed(() => {
  if (!minecraftVersion.value) return 'My instance'
  return loader.value === 'vanilla' ? `Minecraft ${minecraftVersion.value}` : `${loaderName.value} ${minecraftVersion.value}`
})

const loaderHint = computed(() => {
  if (loadingLoaderVersions.value) return 'Checking which builds support this Minecraft version.'
  if (loaderVersions.value.length === 0) return `Pick another Minecraft version or loader; ${loaderName.value} has no build for ${minecraftVersion.value}.`
  return 'The recommended build is picked for you. You can change it later.'
})

const modpackFileName = computed(() => modpackPath.value?.split(/[\\/]/).pop() ?? '')

const canSubmit = computed(() => {
  if (creating.value) return false
  if (mode.value === 'import') return !!modpackPath.value
  if (!minecraftVersion.value) return false
  if (loader.value !== 'vanilla' && !loaderVersion.value) return false
  return true
})

async function refreshLoaderVersions() {
  const requestId = ++loaderRequest
  loaderVersions.value = []
  loaderVersion.value = ''
  if (loader.value === 'vanilla' || !minecraftVersion.value) return
  loadingLoaderVersions.value = true
  try {
    const list = await getLoaderVersions(loader.value, minecraftVersion.value)
    if (requestId !== loaderRequest) return
    loaderVersions.value = list
    const preferred = list.find((v) => v.recommended) ?? list.find((v) => v.stable) ?? list[0]
    loaderVersion.value = preferred?.version ?? ''
  } catch (e) {
    if (requestId === loaderRequest) submitError.value = `Could not load ${loaderName.value} versions: ${String(e)}`
  } finally {
    if (requestId === loaderRequest) loadingLoaderVersions.value = false
  }
}

watch([minecraftVersion, loader], () => {
  submitError.value = null
  refreshLoaderVersions()
  if (supportsMods.value) void refreshClientAvailability(minecraftVersion.value)
})

watch(showSnapshots, () => {
  if (!filteredVersions.value.some((v) => v.id === minecraftVersion.value)) {
    minecraftVersion.value = filteredVersions.value[0]?.id ?? ''
  }
})

async function pickModpack() {
  try {
    const selected = await open({
      multiple: false,
      directory: false,
      filters: [{ name: 'Modrinth modpack', extensions: ['mrpack'] }]
    })
    if (typeof selected === 'string') modpackPath.value = selected
  } catch (e) {
    notifications.notifyError('Could not open file picker', String(e))
  }
}

async function submit() {
  if (!canSubmit.value) return
  submitError.value = null
  creating.value = true
  try {
    let instance: Instance
    if (mode.value === 'import' && modpackPath.value) {
      instance = await importModpackFile(modpackPath.value, importName.value.trim() || null)
      installs.begin(instance.id, 'Preparing installation')
      await instanceStore.load(true)
      notifications.notifyInfo('Importing modpack', `${instance.name} is being installed in the background.`)
    } else {
      instance = await instanceStore.create({
        name: name.value.trim() || suggestedName.value,
        description: description.value.trim(),
        minecraft_version: minecraftVersion.value,
        mod_loader: loader.value,
        loader_version: loader.value === 'vanilla' ? null : loaderVersion.value
      })
      const updated = await instanceStore.update(instance.id, {
        icon_style: `block:${iconMaterial.value}`,
        ...(group.value.trim() ? { group_name: group.value.trim() } : {})
      })
      if (updated) instance = updated
      const extras = supportsMods.value && ((withClient.value && clientOffered.value) || withPerformance.value)
      notifications.notifySuccess(
        'Instance created',
        extras ? `${instance.name} is ready. Mods are being added in the background.` : `${instance.name} is ready. Press Play to download and start it.`
      )
      if (supportsMods.value) {
        void setup.runExtras(instance, withClient.value && clientOffered.value, withPerformance.value)
      }
    }
    emit('created', instance)
    emit('close')
  } catch (e) {
    submitError.value = String(e)
  } finally {
    creating.value = false
  }
}

function handleClose() {
  if (!creating.value) emit('close')
}

onMounted(async () => {
  loadingVersions.value = true
  try {
    versions.value = await fetchVersions()
    minecraftVersion.value = filteredVersions.value[0]?.id ?? versions.value[0]?.id ?? ''
  } catch (e) {
    versionsError.value = `Could not load Minecraft versions: ${String(e)}`
  } finally {
    loadingVersions.value = false
  }
})
useEscapeKey(() => true, handleClose)
</script>

<style scoped lang="scss">
.create__mode {
  align-self: flex-start;
}

.create__preview {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background:
    linear-gradient(120deg, color-mix(in srgb, var(--accent) 16%, transparent), transparent 70%),
    var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.create__preview-icon {
  position: relative;
  flex-shrink: 0;
  padding: 0;
  background: none;
  border: none;
  cursor: pointer;
  transition: transform var(--transition-fast);

  &:hover:not(:disabled) {
    transform: rotate(-4deg) scale(1.04);
  }
}

.create__preview-shuffle {
  position: absolute;
  right: -6px;
  bottom: -6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: var(--accent-text);
  background-color: var(--accent);
  border-radius: 50%;
  box-shadow: 0 0 0 3px var(--bg-elevated);
}

.create__preview-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.create__name {
  width: 100%;
  padding: 0.2rem 0;
  font-family: var(--font-pixel);
  font-size: 1.2rem;
  color: var(--text-primary);
  background: none;
  border: none;
  border-bottom: 1px dashed var(--border-strong);
  outline: none;

  &::placeholder {
    color: var(--text-dim);
  }

  &:focus {
    border-bottom-color: var(--accent);
  }
}

.create__summary {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.create__loader-art {
  width: 28px;
  height: 28px;
  margin-bottom: 0.25rem;
  image-rendering: pixelated;
  border-radius: var(--edge-radius);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1);
}

.create__row {
  display: flex;
  align-items: center;
  gap: 0.75rem;

  .input {
    flex: 1;
  }
}

.create__snapshots {
  flex-shrink: 0;
}

.create__advanced {
  border: 1px dashed var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.6rem 0.8rem;

  summary {
    cursor: pointer;
    font-family: var(--font-pixel);
    font-size: 0.85rem;
    color: var(--text-secondary);
  }
}

.create__advanced-body {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  margin-top: 0.8rem;
}

.create__import {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 1.5rem;
  text-align: center;
  color: var(--accent);
  border: 1px dashed var(--border-color);
  border-radius: var(--edge-radius);
}

.create__import-text {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.25;
}

.create__import-file {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  color: var(--text-primary);
  word-break: break-all;
}

.create__client {
  align-items: center;
  gap: 0.9rem;
  width: 100%;
  padding: 0.85rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  background: var(--bg-tertiary);
  cursor: pointer;
}

.create__client-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  color: var(--text-primary);
}
</style>
