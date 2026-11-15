<template>
  <div v-if="open" class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal__header">
        <h3 class="modal__title">Appearance</h3>
        <button class="modal__close" @click="$emit('close')">×</button>
      </div>

      <div class="modal__section">
        <h3 class="modal__section-title">Theme</h3>
        <div class="theme-toggle">
          <button
            type="button"
            class="theme-toggle__btn"
            :class="theme === 'dark' ? 'theme-toggle__btn--active' : ''"
            @click="setTheme('dark')"
          >
            Dark
          </button>
          <button
            type="button"
            class="theme-toggle__btn"
            :class="theme === 'light' ? 'theme-toggle__btn--active' : ''"
            @click="setTheme('light')"
          >
            Light
          </button>
        </div>
      </div>

      <div class="modal__section">
        <h3 class="modal__section-title">Accent</h3>
        <div class="color-presets">
          <button
            v-for="color in presetColors"
            :key="color"
            type="button"
            class="color-preset"
            :class="accent === color ? 'color-preset--selected' : ''"
            :style="{ backgroundColor: color }"
            :aria-label="`Accent ${color}`"
            @click="setAccent(color)"
          />
          <input
            v-model="customAccent"
            type="color"
            class="custom-color"
            aria-label="Custom accent color"
            @change="setAccent(customAccent)"
          />
        </div>
      </div>

      <div class="modal__section">
        <h3 class="modal__section-title">Corner style</h3>
        <div class="corner-toggle">
          <button
            type="button"
            class="corner-toggle__btn"
            :class="corner === 'blocky' ? 'corner-toggle__btn--active' : ''"
            @click="setCorner('blocky')"
          >
            <span class="corner-toggle__icon corner-toggle__icon--blocky"></span>
            Blocky
          </button>
          <button
            type="button"
            class="corner-toggle__btn"
            :class="corner === 'rounded' ? 'corner-toggle__btn--active' : ''"
            @click="setCorner('rounded')"
          >
            <span class="corner-toggle__icon corner-toggle__icon--rounded"></span>
            Rounded
          </button>
        </div>

        <div class="rounding-control">
          <input
            v-model.number="cornerRadius"
            type="range"
            min="0"
            max="12"
            class="rounding-slider"
            aria-label="Corner radius"
            @input="setCornerRadius(cornerRadius)"
          />
          <div class="rounding-preview">
            <span
              class="rounding-preview__box"
              :style="{ borderRadius: cornerRadius + 'px' }"
            ></span>
            <span class="rounding-preview__value">{{ cornerRadius }}px</span>
          </div>
        </div>
      </div>

      <div class="modal__section modal__section--sync">
        <h3 class="modal__section-title">Sync to desktop</h3>
        <p class="modal__sync-hint">
          Send these preferences to the Pretupid Launcher. The launcher will open and apply them.
        </p>
        <button type="button" class="btn modal__sync-btn" @click="syncToApp">
          Sync to App
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useLauncherBridge } from '~/composables/useLauncherBridge'

defineProps<{
  open: boolean
}>()

defineEmits<{
  (e: 'close'): void
}>()

const { syncSettings } = useLauncherBridge()

const theme = ref<'dark' | 'light'>('dark')
const accent = ref('#4a9eff')
const customAccent = ref('#4a9eff')
const corner = ref<'blocky' | 'rounded'>('blocky')
const cornerRadius = ref(0)
const presetColors = ['#4a9eff', '#4aff9e', '#ff6b6b', '#f9ca24', '#a29bfe', '#fd79a8']
const STORAGE_KEY = 'pretupid-site-settings'

function apply() {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.style.setProperty('--accent', accent.value)
  root.style.setProperty('--accent-hover', shadeColor(accent.value, -20))
  root.style.setProperty('--accent-text', getContrastYIQ(accent.value))
  root.style.setProperty(
    '--edge-radius',
    corner.value === 'blocky' ? '0px' : cornerRadius.value + 'px'
  )

  if (theme.value === 'light') {
    root.style.setProperty('--bg-primary', '#f0f0f0')
    root.style.setProperty('--bg-secondary', '#ffffff')
    root.style.setProperty('--bg-tertiary', '#e6e6e6')
    root.style.setProperty('--text-primary', '#1a1a1a')
    root.style.setProperty('--text-secondary', '#555555')
    root.style.setProperty('--border-color', '#cccccc')
    root.style.setProperty('--shadow-color', 'rgba(0,0,0,0.15)')
  } else {
    root.style.setProperty('--bg-primary', '#0d0d0d')
    root.style.setProperty('--bg-secondary', '#1a1a1a')
    root.style.setProperty('--bg-tertiary', '#262626')
    root.style.setProperty('--text-primary', '#ffffff')
    root.style.setProperty('--text-secondary', '#b3b3b3')
    root.style.setProperty('--border-color', '#404040')
    root.style.setProperty('--shadow-color', 'rgba(0,0,0,0.5)')
  }

  if (typeof window !== 'undefined') {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        theme: theme.value,
        accent: accent.value,
        corner: corner.value,
        cornerRadius: cornerRadius.value
      })
    )
  }
}

function setTheme(value: 'dark' | 'light') {
  theme.value = value
  apply()
}

function setAccent(color: string) {
  accent.value = color
  apply()
}

function setCorner(value: 'blocky' | 'rounded') {
  corner.value = value
  if (value === 'blocky') {
    cornerRadius.value = 0
  } else if (cornerRadius.value === 0) {
    cornerRadius.value = 6
  }
  apply()
}

function setCornerRadius(value: number) {
  cornerRadius.value = value
  corner.value = value > 0 ? 'rounded' : 'blocky'
  apply()
}

function syncToApp() {
  syncSettings({
    theme: theme.value,
    accent: accent.value,
    edge: corner.value === 'blocky' ? 0 : cornerRadius.value
  })
}

function shadeColor(color: string, percent: number) {
  const num = parseInt(color.replace('#', ''), 16)
  const amt = Math.round(2.55 * percent)
  const R = Math.max(0, Math.min(255, (num >> 16) + amt))
  const G = Math.max(0, Math.min(255, ((num >> 8) & 0x00ff) + amt))
  const B = Math.max(0, Math.min(255, (num & 0x0000ff) + amt))
  return `#${(0x1000000 + R * 0x10000 + G * 0x100 + B).toString(16).slice(1)}`
}

function getContrastYIQ(hex: string) {
  const r = parseInt(hex.substring(1, 3), 16)
  const g = parseInt(hex.substring(3, 5), 16)
  const b = parseInt(hex.substring(5, 7), 16)
  return (r * 299 + g * 587 + b * 114) / 1000 >= 128 ? '#000000' : '#ffffff'
}

onMounted(() => {
  if (typeof window === 'undefined') return
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      theme.value = parsed.theme === 'light' ? 'light' : 'dark'
      accent.value = typeof parsed.accent === 'string' ? parsed.accent : '#4a9eff'
      corner.value = parsed.corner === 'rounded' ? 'rounded' : 'blocky'
      cornerRadius.value = typeof parsed.cornerRadius === 'number' ? parsed.cornerRadius : 0
      customAccent.value = accent.value
    } catch {
      void 0
    }
  }
  apply()
})
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4000;
  padding: 1rem;
}

.modal {
  width: 100%;
  max-width: 520px;
  max-height: 85vh;
  overflow-y: auto;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal__title {
  font-family: var(--font-pixel);
  font-size: 1rem;
  text-transform: uppercase;
  margin: 0;
}

.modal__close {
  font-size: 1.75rem;
  line-height: 1;
  color: var(--text-secondary);
  background: none;
  border: none;
  cursor: pointer;
}

.modal__close:hover {
  color: var(--text-primary);
}

.modal__section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.modal__section-title {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
  margin: 0;
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
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  font-family: var(--font-body);
  font-size: 1.05rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
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
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.color-preset {
  width: 34px;
  height: 34px;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: transform var(--transition-fast), border-color var(--transition-fast);
}

.color-preset:hover {
  transform: scale(1.1);
}

.color-preset--selected {
  border-color: var(--text-primary);
}

.custom-color {
  width: 34px;
  height: 34px;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  background: none;
  cursor: pointer;
}

.corner-toggle__icon {
  width: 18px;
  height: 18px;
  border: 2px solid currentColor;
  display: inline-block;
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
  height: 6px;
  appearance: none;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: 3px;
  cursor: pointer;
}

.rounding-slider::-webkit-slider-thumb {
  appearance: none;
  width: 18px;
  height: 18px;
  background-color: var(--accent);
  border: 2px solid var(--border-color);
  border-radius: 3px;
  cursor: pointer;
}

.rounding-slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  background-color: var(--accent);
  border: 2px solid var(--border-color);
  border-radius: 3px;
  cursor: pointer;
}

.rounding-preview {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.rounding-preview__box {
  width: 56px;
  height: 40px;
  background-color: var(--accent);
  border: 2px solid var(--border-color);
  transition: border-radius var(--transition-normal);
}

.rounding-preview__value {
  font-family: var(--font-body);
  font-size: 1rem;
  color: var(--text-secondary);
}

.modal__section--sync {
  border-top: 2px solid var(--border-color);
  padding-top: 1.25rem;
}

.modal__sync-hint {
  margin: 0;
  font-size: 0.95rem;
  color: var(--text-secondary);
}

.modal__sync-btn {
  align-self: flex-start;
}
</style>