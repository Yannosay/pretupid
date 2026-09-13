<template>
  <div v-if="open" class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal__header">
        <h3 class="modal__title">Settings</h3>
        <button class="modal__close" @click="$emit('close')">×</button>
      </div>
      <div class="modal__section">
        <h3>Theme</h3>
        <div class="theme-toggle">
          <button :class="['theme-toggle__btn', theme === 'dark' ? 'theme-toggle__btn--active' : '']" @click="setTheme('dark')">Dark</button>
          <button :class="['theme-toggle__btn', theme === 'light' ? 'theme-toggle__btn--active' : '']" @click="setTheme('light')">Light</button>
        </div>
      </div>
      <div class="modal__section">
        <h3>Accent Color</h3>
        <div class="color-presets">
          <button v-for="color in presetColors" :key="color" :class="['color-preset', accent === color ? 'color-preset--selected' : '']" :style="{ backgroundColor: color }" @click="setAccent(color)"></button>
          <div class="custom-color">
            <input v-model="customAccent" type="color" @change="setAccent(customAccent)">
          </div>
        </div>
      </div>
      <div class="modal__section">
        <h3>Corner Style</h3>
        <div class="corner-toggle">
          <button :class="['corner-toggle__btn', corner === 'blocky' ? 'corner-toggle__btn--active' : '']" @click="setCorner('blocky')">
            <span class="corner-toggle__icon corner-toggle__icon--blocky"></span> Blocky
          </button>
          <button :class="['corner-toggle__btn', corner === 'rounded' ? 'corner-toggle__btn--active' : '']" @click="setCorner('rounded')">
            <span class="corner-toggle__icon corner-toggle__icon--rounded"></span> Rounded
          </button>
        </div>
        <div class="rounding-control">
          <input v-model="cornerRadius" type="range" min="0" max="12" class="rounding-slider" @input="setCornerRadius(cornerRadius)">
          <div class="rounding-preview">
            <span class="rounding-preview__box" :style="{ borderRadius: cornerRadius + 'px' }"></span>
            <span class="rounding-preview__value">{{ cornerRadius }}px</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  open: boolean
}>()
defineEmits<{
  (e: 'close'): void
}>()

const theme = ref('dark')
const accent = ref('#4a9eff')
const customAccent = ref('#4a9eff')
const corner = ref('blocky')
const cornerRadius = ref(0)
const presetColors = ['#4a9eff', '#4aff9e', '#ff6b6b', '#f9ca24', '#a29bfe', '#fd79a8']

function applySettings() {
  const root = document.documentElement
  root.style.setProperty('--accent', accent.value)
  root.style.setProperty('--accent-hover', shadeColor(accent.value, -20))
  root.style.setProperty('--accent-text', getContrastYIQ(accent.value))
  root.style.setProperty('--edge-radius', corner.value === 'blocky' ? '0px' : cornerRadius.value + 'px')
  if (theme.value === 'light') {
    root.style.setProperty('--bg-primary', '#f0f0f0')
    root.style.setProperty('--bg-secondary', '#ffffff')
    root.style.setProperty('--bg-tertiary', '#e6e6e6')
    root.style.setProperty('--text-primary', '#1a1a1a')
    root.style.setProperty('--text-secondary', '#555555')
    root.style.setProperty('--border-color', '#cccccc')
    root.style.setProperty('--shadow-color', 'rgba(0,0,0,0.2)')
  } else {
    root.style.setProperty('--bg-primary', '#0d0d0d')
    root.style.setProperty('--bg-secondary', '#1a1a1a')
    root.style.setProperty('--bg-tertiary', '#262626')
    root.style.setProperty('--text-primary', '#ffffff')
    root.style.setProperty('--text-secondary', '#b3b3b3')
    root.style.setProperty('--border-color', '#404040')
    root.style.setProperty('--shadow-color', 'rgba(0,0,0,0.5)')
  }
  localStorage.setItem('pretupid-settings', JSON.stringify({
    theme: theme.value,
    accent: accent.value,
    corner: corner.value,
    cornerRadius: cornerRadius.value
  }))
}

function setTheme(value: string) {
  theme.value = value
  applySettings()
}

function setAccent(color: string) {
  accent.value = color
  applySettings()
}

function setCorner(value: string) {
  corner.value = value
  if (value === 'blocky') cornerRadius.value = 0
  applySettings()
}

function setCornerRadius(value: number) {
  cornerRadius.value = value
  corner.value = 'rounded'
  applySettings()
}

function shadeColor(color: string, percent: number) {
  const num = parseInt(color.replace('#',''), 16)
  const amt = Math.round(2.55 * percent)
  const R = Math.max(0, Math.min(255, (num >> 16) + amt))
  const G = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amt))
  const B = Math.max(0, Math.min(255, (num & 0x0000FF) + amt))
  return `#${(0x1000000 + R*0x10000 + G*0x100 + B).toString(16).slice(1)}`
}

function getContrastYIQ(hex: string) {
  const r = parseInt(hex.substring(1,3),16)
  const g = parseInt(hex.substring(3,5),16)
  const b = parseInt(hex.substring(5,7),16)
  return (r*299 + g*587 + b*114) / 1000 >= 128 ? '#000000' : '#ffffff'
}

onMounted(() => {
  const saved = localStorage.getItem('pretupid-settings')
  if (saved) {
    const parsed = JSON.parse(saved)
    theme.value = parsed.theme || 'dark'
    accent.value = parsed.accent || '#4a9eff'
    corner.value = parsed.corner || 'blocky'
    cornerRadius.value = parsed.cornerRadius || 0
    customAccent.value = accent.value
  }
  applySettings()
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/_mixins' as *;

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4000;
}

.modal {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  width: 90%;
  max-width: 520px;
  max-height: 80vh;
  overflow-y: auto;
  padding: 1.5rem;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.modal__title {
  font-family: var(--font-pixel);
  font-size: 1.5rem;
}

.modal__close {
  font-size: 2rem;
  line-height: 1;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
}

.modal__section {
  margin-bottom: 1.5rem;
}

.modal__section h3 {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
  text-transform: uppercase;
}

.theme-toggle {
  display: flex;
  gap: 0.75rem;
}

.theme-toggle__btn {
  padding: 0.6rem 1.2rem;
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  background-color: var(--bg-tertiary);
  color: var(--text-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast), border-color var(--transition-fast);
}

.theme-toggle__btn:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.theme-toggle__btn--active {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}

.color-presets {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}

.color-preset {
  width: 30px;
  height: 30px;
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
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.custom-color input[type='color'] {
  width: 30px;
  height: 30px;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  background: none;
  cursor: pointer;
}

.corner-toggle {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.corner-toggle__btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  background-color: var(--bg-tertiary);
  color: var(--text-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast), border-color var(--transition-fast);
}

.corner-toggle__btn:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.corner-toggle__btn--active {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}

.corner-toggle__icon {
  width: 18px;
  height: 18px;
  background-color: transparent;
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
  gap: 1rem;
}

.rounding-preview__box {
  width: 60px;
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
</style>