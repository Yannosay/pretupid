import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { emit } from '@tauri-apps/api/event'
import { invoke } from '@tauri-apps/api/core'
import { getGlobalSettings } from '@/api'

export type Theme = 'dark' | 'light'
export type FontChoice = 'minecraft' | 'press-start' | 'custom'

interface SettingsState {
  theme: Theme
  accentColor: string
  font: FontChoice
  customFontPath: string | null
  edgeRadius: number
  lastRoundedRadius: number
  globalSettings: {
    default_java_path: string | null
    auto_install_java: boolean
    show_snapshots: boolean
    auto_update_enabled: boolean
  }
}

const DEFAULT_ACCENT = '#4a9eff'

function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const clean = hex.replace('#', '')
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean
  const num = parseInt(full, 16)
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255
  }
}

function luminance(r: number, g: number, b: number): number {
  const a = [r, g, b].map((v) => {
    const s = v / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * a[0] + 0.7152 * a[1] + 0.0722 * a[2]
}

function getContrastingText(hex: string): string {
  const { r, g, b } = hexToRgb(hex)
  const lum = luminance(r, g, b)
  return lum > 0.35 ? '#000000' : '#ffffff'
}

function darken(hex: string, amount: number): string {
  const { r, g, b } = hexToRgb(hex)
  const factor = 1 - amount
  const toHex = (v: number) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')
  return `#${toHex(r * factor)}${toHex(g * factor)}${toHex(b * factor)}`
}

function applySettings(state: SettingsState) {
  const root = document.documentElement

  const palette = state.theme === 'dark' ? DARK_PALETTE : LIGHT_PALETTE

  const accent = state.accentColor
  const accentHover = darken(accent, 0.1)
  const accentText = getContrastingText(accent)
  const edgeRadius = `${state.edgeRadius}px`

  for (const [name, value] of Object.entries(palette)) {
    root.style.setProperty(name, value)
  }
  root.dataset.theme = state.theme
  root.dataset.font = state.font === 'custom' && !customFontReady ? 'minecraft' : state.font
  if (state.font === 'custom' && state.customFontPath && state.customFontPath !== loadedFontPath) {
    void loadCustomFont(state.customFontPath)
  }
  root.style.setProperty('--accent', accent)
  root.style.setProperty('--accent-hover', accentHover)
  root.style.setProperty('--accent-text', accentText)
  root.style.setProperty('--edge-radius', edgeRadius)
}

const STORAGE_KEY = 'pretupid-settings'
const CUSTOM_FONT_FAMILY = 'PretupidCustom'

const DARK_PALETTE: Record<string, string> = {
  '--bg-primary': '#07090d',
  '--bg-secondary': 'rgba(16, 20, 28, 0.86)',
  '--bg-tertiary': 'rgba(255, 255, 255, 0.06)',
  '--bg-elevated': 'rgba(22, 27, 37, 0.94)',
  '--surface-hover': 'rgba(255, 255, 255, 0.1)',
  '--text-primary': '#f2f4f8',
  '--text-secondary': '#a3abb9',
  '--text-dim': '#6c7584',
  '--border-color': 'rgba(255, 255, 255, 0.1)',
  '--border-strong': 'rgba(255, 255, 255, 0.18)',
  '--highlight': 'rgba(255, 255, 255, 0.07)',
  '--shadow-color': 'rgba(0, 0, 0, 0.45)'
}

const LIGHT_PALETTE: Record<string, string> = {
  '--bg-primary': '#e9edf3',
  '--bg-secondary': 'rgba(255, 255, 255, 0.82)',
  '--bg-tertiary': 'rgba(10, 20, 40, 0.05)',
  '--bg-elevated': 'rgba(255, 255, 255, 0.95)',
  '--surface-hover': 'rgba(10, 20, 40, 0.09)',
  '--text-primary': '#111722',
  '--text-secondary': '#4b5565',
  '--text-dim': '#7b8494',
  '--border-color': 'rgba(10, 20, 40, 0.12)',
  '--border-strong': 'rgba(10, 20, 40, 0.22)',
  '--highlight': 'rgba(255, 255, 255, 0.7)',
  '--shadow-color': 'rgba(10, 20, 40, 0.15)'
}

let loadedFontPath: string | null = null
let customFontReady = false
let customFontFace: FontFace | null = null

async function loadCustomFont(path: string) {
  loadedFontPath = path
  try {
    const bytes = await invoke<ArrayBuffer>('read_font_file', { path })
    const face = new FontFace(CUSTOM_FONT_FAMILY, bytes)
    await face.load()
    if (customFontFace) document.fonts.delete(customFontFace)
    document.fonts.add(face)
    customFontFace = face
    customFontReady = true
    document.documentElement.dataset.font = 'custom'
  } catch (error) {
    customFontReady = false
    document.documentElement.dataset.font = 'minecraft'
    console.error('[pretupid] custom font failed to load:', error)
  }
}

function defaultSettings(): SettingsState {
  return {
    theme: 'dark',
    accentColor: DEFAULT_ACCENT,
    font: 'minecraft',
    customFontPath: null,
    edgeRadius: 0,
    lastRoundedRadius: 8,
    globalSettings: {
      default_java_path: null,
      auto_install_java: true,
      show_snapshots: false,
      auto_update_enabled: true
    }
  }
}

function loadSettingsFromStorage(): SettingsState {
  const base = defaultSettings()
  const stored = localStorage.getItem(STORAGE_KEY)
  if (!stored) return base
  try {
    const parsed = JSON.parse(stored)
    return {
      theme: parsed.theme ?? base.theme,
      accentColor: parsed.accentColor ?? base.accentColor,
      font: parsed.font === 'press-start' || parsed.font === 'custom' ? parsed.font : 'minecraft',
      customFontPath: typeof parsed.customFontPath === 'string' ? parsed.customFontPath : null,
      edgeRadius: typeof parsed.edgeRadius === 'number' ? parsed.edgeRadius : base.edgeRadius,
      lastRoundedRadius:
        typeof parsed.lastRoundedRadius === 'number'
          ? parsed.lastRoundedRadius
          : base.lastRoundedRadius,
      globalSettings: {
        default_java_path: parsed.globalSettings?.default_java_path ?? null,
        auto_install_java: parsed.globalSettings?.auto_install_java ?? true,
        show_snapshots: parsed.globalSettings?.show_snapshots ?? false,
        auto_update_enabled: parsed.globalSettings?.auto_update_enabled ?? true
      }
    }
  } catch {
    return base
  }
}

let broadcastTimer: number | null = null

function broadcastSettings(state: SettingsState) {
  if (broadcastTimer !== null) {
    window.clearTimeout(broadcastTimer)
  }
  broadcastTimer = window.setTimeout(() => {
    broadcastTimer = null
    emit('settings://changed', state).catch(() => void 0)
  }, 60)
}

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<SettingsState>(loadSettingsFromStorage())

  watch(
    settings,
    (newSettings) => {
      applySettings(newSettings)
      localStorage.setItem(STORAGE_KEY, JSON.stringify(newSettings))
      broadcastSettings(newSettings)
    },
    { deep: true, immediate: true }
  )

  function setTheme(theme: Theme) {
    settings.value.theme = theme
  }

  function setAccentColor(color: string) {
    settings.value.accentColor = color
  }

  function setFont(font: FontChoice) {
    settings.value.font = font
  }

  function setCustomFontPath(path: string | null) {
    settings.value.customFontPath = path
    settings.value.font = path ? 'custom' : 'minecraft'
  }

  function setEdgeStyle(style: 'blocky' | 'rounded') {
    if (style === 'blocky') {
      settings.value.edgeRadius = 0
    } else {
      settings.value.edgeRadius = settings.value.lastRoundedRadius
    }
  }

  function setEdgeRadius(radius: number) {
    settings.value.edgeRadius = radius
    settings.value.lastRoundedRadius = radius
  }

  function resetToDefaults() {
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      void 0
    }
    settings.value = defaultSettings()
  }

  function setGlobalSettings(value: {
    default_java_path: string | null
    auto_install_java: boolean
    show_snapshots: boolean
    auto_update_enabled: boolean
  }) {
    settings.value.globalSettings = value
  }

  function loadLocalSettings() {
    settings.value = loadSettingsFromStorage()
  }

  function applyExternalSettings(state: SettingsState) {
    settings.value = state
  }

  async function loadGlobalSettings() {
    try {
      const gs = await getGlobalSettings()
      settings.value.globalSettings = {
        ...settings.value.globalSettings,
        ...gs
      }
    } catch (e) {
      console.error('Failed to load global settings:', e)
    }
  }

  function resetGlobalSettings() {
    settings.value.globalSettings = {
      default_java_path: null,
      auto_install_java: true,
      show_snapshots: false,
      auto_update_enabled: true
    }
  }

  return {
    settings,
    setTheme,
    setAccentColor,
    setFont,
    setCustomFontPath,
    setEdgeStyle,
    setEdgeRadius,
    setGlobalSettings,
    loadLocalSettings,
    applyExternalSettings,
    loadGlobalSettings,
    resetGlobalSettings,
    resetToDefaults
  }
})