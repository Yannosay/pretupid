<template>
  <div v-if="modelValue" class="modal-overlay" @click.self="close">
    <div class="modal">
      <div class="modal__header">
        <h2 class="modal__title">Settings</h2>
        <button class="modal__close" @click="close">×</button>
      </div>

      <div class="modal__body">
        <section class="modal__section">
          <h3>Theme</h3>
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

        <section class="modal__section">
          <h3>Accent Color</h3>
          <div class="color-presets">
            <button
              v-for="color in presetColors"
              :key="color"
              class="color-preset"
              :style="{ backgroundColor: color }"
              :class="{ 'color-preset--selected': settings.accentColor === color }"
              @click="settingsStore.setAccentColor(color)"
            />
          </div>
          <div class="custom-color">
            <input
              type="color"
              :value="settings.accentColor"
              @input="settingsStore.setAccentColor(($event.target as HTMLInputElement).value)"
            />
            <span>Custom</span>
          </div>
        </section>

        <section class="modal__section">
          <h3>Corner Style</h3>
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

        <section class="modal__section">
          <h3>Minecraft Versions</h3>
          <label class="settings-checkbox">
            <input
              v-model="showSnapshots"
              type="checkbox"
              class="settings-checkbox__input"
              @change="saveJavaSettings"
            />
            <span class="settings-checkbox__box" aria-hidden="true"></span>
            <span class="settings-checkbox__label">Show non-release versions</span>
          </label>
        </section>

        <section class="modal__section">
          <h3>Java</h3>
          <div class="java-settings">
            <div class="form-group">
              <label class="form-label">Default Java Path</label>
              <div class="java-path-row">
                <input
                  v-model="javaPath"
                  class="input"
                  placeholder="Auto-detect"
                  @change="saveJavaSettings"
                />
                <button class="btn btn--ghost" @click="resetJavaPath">
                  Reset
                </button>
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

        <section class="modal__section">
          <button class="btn btn--danger" @click="resetSettings">
            Reset to Defaults
          </button>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useSettingsStore } from '@/stores/settings'
import { getGlobalSettings, saveGlobalSettings } from '@/api'
import { computed, ref, onMounted } from 'vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const settingsStore = useSettingsStore()
const settings = computed(() => settingsStore.settings)

const presetColors = ['#ffffff', '#4aff9e', '#5a9eff', '#ff9eb5', '#b47aff']

const edgeStyle = computed(() => settings.value.edgeRadius === 0 ? 'blocky' : 'rounded')

const cornerRadius = computed(() => {
  const radius = settings.value.edgeRadius
  return radius === 0 ? settings.value.lastRoundedRadius : radius
})

function setEdgeStyle(style: 'blocky' | 'rounded') {
  settingsStore.setEdgeStyle(style)
}

function updateCornerRadius(value: string) {
  settingsStore.setEdgeRadius(parseInt(value, 10))
}

const javaPath = ref<string>('')
const autoInstallJava = ref(true)
const showSnapshots = ref(false)

onMounted(async () => {
  try {
    const gs = await getGlobalSettings()
    javaPath.value = gs.default_java_path ?? ''
    autoInstallJava.value = gs.auto_install_java
    showSnapshots.value = gs.show_snapshots
  } catch (e) {
    console.error(e)
  }
})

async function saveJavaSettings() {
  try {
    await saveGlobalSettings({
      default_java_path: javaPath.value.trim() || null,
      auto_install_java: autoInstallJava.value,
      show_snapshots: showSnapshots.value
    })
  } catch (e) {
    console.error(e)
  }
}

function resetJavaPath() {
  javaPath.value = ''
  saveJavaSettings()
}

function resetSettings() {
  settingsStore.resetToDefaults()
  javaPath.value = ''
  autoInstallJava.value = true
  showSnapshots.value = false
  saveJavaSettings()
}

function close() {
  emit('update:modelValue', false)
}
</script>
<style scoped lang="scss">
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  width: 90%;
  max-width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
}

.modal__header {
  position: sticky;
  top: -1.5rem;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -1.5rem -1.5rem 1.5rem -1.5rem;
  padding: 1.5rem;
  background-color: var(--bg-secondary);
  border-bottom: 2px solid var(--border-color);
  border-radius: var(--edge-radius) var(--edge-radius) 0 0;
}

.modal__title {
  font-size: 1.5rem;
}

.modal__close {
  font-size: 2rem;
  line-height: 1;
  color: var(--text-secondary);
  transition: color var(--transition-fast);
  cursor: pointer;
  border: none;
  background: none;
  padding: 0.25rem 0.5rem;
}

.modal__close:hover {
  color: var(--text-primary);
}

.modal__body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.modal__section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.modal__section h3 {
  font-size: 1rem;
  margin: 0;
}

.theme-toggle {
  display: flex;
  gap: 0.5rem;
}

.theme-toggle__btn {
  flex: 1;
  padding: 0.75rem;
  font-family: var(--font-body);
  font-size: 1rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-primary);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast);
}

.theme-toggle__btn--active {
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
  border: 2px solid var(--border-color);
  cursor: pointer;
  transition: transform var(--transition-fast),
              border-color var(--transition-fast);
}

.color-preset--selected {
  border-color: var(--text-primary);
  transform: scale(1.1);
}

.custom-color {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.custom-color input {
  width: 36px;
  height: 36px;
  border: none;
  cursor: pointer;
}

.corner-toggle {
  display: flex;
  gap: 0.5rem;
}

.corner-toggle__btn {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  font-family: var(--font-body);
  font-size: 1rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-primary);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast);
}

.corner-toggle__btn--active {
  background-color: var(--accent);
  border-color: var(--accent);
  color: var(--accent-text);
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
  border: 2px solid var(--border-color);
}

.rounding-preview__value {
  font-family: var(--font-body);
  color: var(--text-secondary);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  display: block;
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.input {
  padding: 0.75rem;
  background-color: var(--bg-primary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 1rem;
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

.settings-checkbox {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-primary);
  user-select: none;
  min-height: 32px;
}

.settings-checkbox__input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.settings-checkbox__box {
  position: relative;
  width: 50px;
  height: 28px;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast);
  flex-shrink: 0;
}

.settings-checkbox__box::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background-color: var(--text-secondary);
  border-radius: var(--edge-radius);
  transition: transform var(--transition-fast),
              background-color var(--transition-fast);
}

.settings-checkbox__input:checked + .settings-checkbox__box {
  background-color: var(--accent);
  border-color: var(--accent);
}

.settings-checkbox__input:checked + .settings-checkbox__box::after {
  transform: translateX(22px);
  background-color: var(--accent-text);
}

.settings-checkbox__label {
  color: var(--text-primary);
}

.btn {
  padding: 0.75rem 1.25rem;
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-primary);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast);
}

.btn:hover {
  border-color: var(--accent);
}

.btn--ghost {
  background-color: transparent;
}

.btn--danger {
  background-color: #e74c3c;
  border-color: #c0392b;
  color: white;
}
</style>