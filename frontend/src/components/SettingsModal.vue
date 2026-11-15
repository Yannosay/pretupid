<template>
  <div v-if="modelValue" class="modal-overlay" @click.self="close">
    <div class="modal">
      <div class="modal__header">
        <h2 class="modal__title">Settings</h2>
        <button class="modal__close" @click="close">×</button>
      </div>

      <div class="modal__body">
        <section class="section">
          <h3 class="section__title section__title--sm">Theme</h3>
          <div class="theme-toggle">
            <button
              class="theme-toggle__btn"
              :class="{ 'theme-toggle__btn--active': settings.theme === 'dark' }"
              @click="settingsStore.setTheme('dark')"
            >
              Dark
            </button>
            <button
              class="theme-toggle__btn"
              :class="{ 'theme-toggle__btn--active': settings.theme === 'light' }"
              @click="settingsStore.setTheme('light')"
            >
              Light
            </button>
          </div>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Accent Color</h3>
          <div class="color-presets">
            <button
              v-for="color in presetColors"
              :key="color"
              class="color-preset"
              :style="{ backgroundColor: color }"
              :class="{ 'color-preset--selected': settings.accentColor.toLowerCase() === color }"
              :aria-label="`Use accent ${color}`"
              @click="settingsStore.setAccentColor(color)"
            />
            <button
              class="color-preset color-preset--custom"
              :class="{ 'color-preset--selected': customAccent }"
              :aria-expanded="customOpen"
              aria-label="Custom accent color"
              title="Custom color"
              @click="customOpen = !customOpen"
            >
              <span v-if="customAccent" class="color-preset__fill" :style="{ backgroundColor: settings.accentColor }" />
            </button>
          </div>
          <div v-if="customOpen" class="custom-color">
            <ColorPicker :model-value="settings.accentColor" @update:model-value="settingsStore.setAccentColor" />
          </div>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Font</h3>
          <div class="theme-toggle">
            <button
              v-for="option in fontOptions"
              :key="option.value"
              class="theme-toggle__btn"
              :class="{ 'theme-toggle__btn--active': settings.font === option.value }"
              @click="chooseFont(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
          <p v-if="settings.font === 'custom' && settings.customFontPath" class="settings-font__path">
            {{ settings.customFontPath.split(/[\/]/).pop() }}
          </p>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Home</h3>
          <label class="toggle">
            <input
              type="checkbox"
              class="toggle__input"
              :checked="settings.sceneryMotion"
              @change="settingsStore.setSceneryMotion(($event.target as HTMLInputElement).checked)"
            />
            <span class="toggle__track" />
            <span class="toggle__label">Animated landscape</span>
          </label>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Corner Style</h3>
          <div class="corner-toggle">
            <button
              class="corner-toggle__btn"
              :class="{ 'corner-toggle__btn--active': edgeStyle === 'blocky' }"
              @click="setEdgeStyle('blocky')"
            >
              <span class="corner-toggle__icon corner-toggle__icon--blocky" />
              Blocky
            </button>
            <button
              class="corner-toggle__btn"
              :class="{ 'corner-toggle__btn--active': edgeStyle === 'rounded' }"
              @click="setEdgeStyle('rounded')"
            >
              <span class="corner-toggle__icon corner-toggle__icon--rounded" />
              Rounded
            </button>
          </div>

          <div v-if="edgeStyle === 'rounded'" class="rounding-control">
            <input
              type="range"
              min="1"
              max="16"
              step="1"
              :value="cornerRadius"
              @input="updateCornerRadius(($event.target as HTMLInputElement).value)"
              class="rounding-slider"
            />
            <div class="rounding-preview">
              <div
                class="rounding-preview__box"
                :style="{ borderRadius: `${cornerRadius}px` }"
              />
              <span class="rounding-preview__value">{{ cornerRadius }}px</span>
            </div>
          </div>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Minecraft Versions</h3>
          <label class="settings-checkbox">
            <input v-model="showSnapshots" type="checkbox" class="settings-checkbox__input" />
            <span class="settings-checkbox__box" aria-hidden="true"></span>
            <span class="settings-checkbox__label">Show non-release versions</span>
          </label>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Updates</h3>
          <label class="settings-checkbox">
            <input v-model="autoUpdate" type="checkbox" class="settings-checkbox__input" />
            <span class="settings-checkbox__box" aria-hidden="true"></span>
            <span class="settings-checkbox__label">Automatically check for updates</span>
          </label>

          <div v-if="updaterStore.updateAvailable && updaterStore.updateInfo" class="update-banner">
            <div class="update-banner__info">
              <span class="update-banner__version">Pretupid {{ updaterStore.updateInfo.version }}</span>
              <span class="update-banner__status">Ready to install</span>
            </div>
            <div class="update-banner__actions">
              <button class="btn btn--ghost btn--small" @click="updaterStore.deferUpdate">Remind me later</button>
              <button class="btn btn--small" :disabled="updaterStore.installing" @click="updaterStore.installPending()">
                {{ updaterStore.installing ? 'Installing...' : 'Install Now' }}
              </button>
            </div>
            <div v-if="updaterStore.installing" class="update-progress">
              <div class="update-progress__bar">
                <div class="update-progress__fill" :style="{ width: `${updaterStore.downloadProgress}%` }" />
              </div>
              <span class="update-progress__text">{{ updaterStore.downloadProgress }}%</span>
            </div>
          </div>

          <div v-else class="update-actions">
            <button class="btn btn--ghost btn--small" :disabled="updaterStore.checking" @click="checkForUpdates">
              {{ updaterStore.checking ? 'Checking...' : 'Check for Updates' }}
            </button>
          </div>
        </section>

        <section class="section">
          <h3 class="section__title section__title--sm">Java</h3>
          <div class="java-settings">
            <div class="form-group">
              <label class="form-label">Default Java Path</label>
              <div class="java-path-row">
                <input v-model="javaPath" class="input" placeholder="Auto-detect" @change="saveJavaSettings" />
                <button class="btn btn--ghost btn--small" @click="resetJavaPath">Reset</button>
              </div>
            </div>
            <label class="settings-checkbox">
              <input
                v-model="autoInstallJava"
                type="checkbox"
                class="settings-checkbox__input"
                @change="saveJavaSettings"
              />
              <span class="settings-checkbox__box" aria-hidden="true"></span>
              <span class="settings-checkbox__label">Auto-install required Java version</span>
            </label>
          </div>
        </section>

        <section class="section">
          <button class="btn btn--danger" @click="resetSettings">Reset to Defaults</button>
        </section>

        <footer v-if="appInfo" class="modal__footer">
          <span class="modal__footer-text">{{ appInfo.name }}</span>
          <span class="modal__footer-version">v{{ appInfo.version }}</span>
          <nav class="settings-legal" aria-label="Legal">
            <button v-for="link in LEGAL_LINKS" :key="link.url" class="settings-legal__link" @click="openLink(link.url)">
              {{ link.label }}
            </button>
          </nav>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ColorPicker from '@ui/ColorPicker.vue'
import { useSettingsStore } from '@/stores/settings'
import { getGlobalSettings, saveGlobalSettings, getAppInfo, openExternalLink } from '@/api'
import { LEGAL_LINKS } from '@/config'
import { useUpdaterStore } from '@/stores/updater'
import { useNotificationsStore } from '@/stores/notifications'
import { computed, ref, onMounted } from 'vue'
import { open } from '@tauri-apps/plugin-dialog'
import type { FontChoice } from '@/stores/settings'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const settingsStore = useSettingsStore()
const updaterStore = useUpdaterStore()
const notifications = useNotificationsStore()
const settings = computed(() => settingsStore.settings)

const presetColors = ['#4a9eff', '#7c5cff', '#e879f9', '#f87171', '#fb923c', '#facc15', '#4ade80', '#2dd4bf']
const customOpen = ref(false)
const customAccent = computed(() => !presetColors.includes(settings.value.accentColor.toLowerCase()))

const fontOptions: { value: FontChoice; label: string }[] = [
  { value: 'minecraft', label: 'Minecraft (Recommended)' },
  { value: 'press-start', label: 'Press Start' },
  { value: 'custom', label: 'Custom font' }
]

async function openLink(url: string) {
  try {
    await openExternalLink(url)
  } catch (e) {
    notifications.notifyError('Could not open link', String(e))
  }
}

async function chooseFont(font: FontChoice) {
  if (font !== 'custom') {
    settingsStore.setFont(font)
    return
  }
  try {
    const selected = await open({
      multiple: false,
      directory: false,
      filters: [{ name: 'Fonts', extensions: ['ttf', 'otf', 'woff', 'woff2'] }]
    })
    if (typeof selected === 'string') {
      settingsStore.setCustomFontPath(selected)
    }
  } catch (e) {
    notifications.notifyError('Could not open file picker', String(e))
  }
}

const edgeStyle = computed(() => (settings.value.edgeRadius === 0 ? 'blocky' : 'rounded'))

const cornerRadius = computed(() => {
  const radius = settings.value.edgeRadius
  return radius === 0 ? settings.value.lastRoundedRadius : radius
})

const showSnapshots = computed({
  get: () => settings.value.globalSettings.show_snapshots,
  set: (value: boolean) => {
    settingsStore.setGlobalSettings({
      ...settings.value.globalSettings,
      show_snapshots: value
    })
    saveJavaSettings()
  }
})

const autoUpdate = computed({
  get: () => settings.value.globalSettings.auto_update_enabled,
  set: (value: boolean) => {
    settingsStore.setGlobalSettings({
      ...settings.value.globalSettings,
      auto_update_enabled: value
    })
    saveJavaSettings()
  }
})

function setEdgeStyle(style: 'blocky' | 'rounded') {
  settingsStore.setEdgeStyle(style)
}

function updateCornerRadius(value: string) {
  settingsStore.setEdgeRadius(parseInt(value, 10))
}

const javaPath = ref<string>('')
const appInfo = ref<{ name: string; version: string } | null>(null)
const autoInstallJava = ref(true)

onMounted(async () => {
  try {
    appInfo.value = await getAppInfo()
    const gs = await getGlobalSettings()
    javaPath.value = gs.default_java_path ?? ''
    autoInstallJava.value = gs.auto_install_java

    settingsStore.setGlobalSettings({
      ...settings.value.globalSettings,
      show_snapshots: gs.show_snapshots,
      default_java_path: gs.default_java_path,
      auto_install_java: gs.auto_install_java,
      auto_update_enabled: gs.auto_update_enabled ?? true
    })
  } catch (e) {
    console.error(e)
  }
})

async function saveJavaSettings() {
  try {
    const globalSettings = {
      default_java_path: javaPath.value.trim() || null,
      auto_install_java: autoInstallJava.value,
      show_snapshots: settings.value.globalSettings.show_snapshots,
      auto_update_enabled: settings.value.globalSettings.auto_update_enabled
    }
    await saveGlobalSettings(globalSettings)
    settingsStore.setGlobalSettings(globalSettings)
  } catch (e) {
    notifications.notifyError('Failed to save settings', String(e))
  }
}

function checkForUpdates() {
  updaterStore.checkForUpdates(true)
}

function resetJavaPath() {
  javaPath.value = ''
  saveJavaSettings()
}

function resetSettings() {
  settingsStore.resetToDefaults()
  javaPath.value = ''
  autoInstallJava.value = true
  saveJavaSettings()
  notifications.notifyInfo('Settings reset', 'Defaults have been restored.')
}

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2500;
}

.modal {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  width: 90%;
  max-width: 560px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  background-color: var(--bg-tertiary);
}

.modal__title {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
  margin: 0;
}

.modal__close {
  font-size: 1.75rem;
  line-height: 1;
  color: var(--text-secondary);
  transition: color var(--transition-fast);
  cursor: pointer;
  border: none;
  background: none;
}

.modal__close:hover {
  color: var(--text-primary);
}

.modal__body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  overflow-y: auto;
  padding: 1.5rem;
}

.theme-toggle,
.corner-toggle {
  display: flex;
  gap: 0.5rem;
}

.theme-toggle__btn,
.corner-toggle__btn {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-content: center;
  padding: 0.75rem;
  font-family: var(--font-body);
  font-size: 1rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-primary);
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
}

.theme-toggle__btn--active,
.corner-toggle__btn--active {
  background-color: var(--accent);
  border-color: var(--accent);
  color: var(--accent-text);
}

.color-presets {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.color-preset {
  width: 36px;
  height: 36px;
  border-radius: var(--edge-radius);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: transform var(--transition-fast), border-color var(--transition-fast);
}

.color-preset--selected {
  border-color: var(--text-primary);
  transform: scale(1.1);
}

.color-preset--custom {
  position: relative;
  overflow: hidden;
  background: conic-gradient(#f87171, #facc15, #4ade80, #2dd4bf, #4a9eff, #7c5cff, #e879f9, #f87171);
}

.color-preset__fill {
  position: absolute;
  inset: 6px;
  border-radius: var(--edge-radius);
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.35);
}

.custom-color {
  margin-top: 0.85rem;
  padding: 0.85rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.corner-toggle__icon {
  width: 20px;
  height: 20px;
  background-color: currentColor;
}

.corner-toggle__icon--blocky {
  border-radius: 0;
}

.corner-toggle__icon--rounded {
  border-radius: 6px;
}

.rounding-control {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 0.75rem;
}

.rounding-slider {
  width: 100%;
  accent-color: var(--accent);
}

.rounding-preview {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.rounding-preview__box {
  width: 48px;
  height: 48px;
  background-color: var(--accent);
  border: 1px solid var(--border-color);
}

.rounding-preview__value {
  font-family: var(--font-body);
  color: var(--text-secondary);
}

.java-settings {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.java-path-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.java-path-row .input {
  flex: 1;
}

.modal__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
}

.modal__footer-text {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.modal__footer-version {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--accent);
}

.update-banner {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--accent);
  border-radius: var(--edge-radius);
  margin-top: 0.5rem;
}

.update-banner__info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.update-banner__version {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--accent);
}

.update-banner__status {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.update-banner__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.update-progress {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.update-progress__bar {
  flex: 1;
  height: 8px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
}

.update-progress__fill {
  height: 100%;
  background-color: var(--accent);
  transition: width 0.2s ease;
}

.update-progress__text {
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--text-secondary);
  min-width: 40px;
}

.update-actions {
  margin-top: 0.5rem;
}

.settings-font__path {
  margin: 0.5rem 0 0;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.settings-legal {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-left: auto;
}

.settings-legal__link {
  padding: 0;
  font: inherit;
  font-size: 0.85rem;
  color: var(--text-secondary);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--accent);
  }
}
</style>