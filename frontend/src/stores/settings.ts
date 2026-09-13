import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { getGlobalSettings } from '@/api'

export type Theme = 'dark' | 'light'

interface SettingsState {
  theme: Theme
  accentColor: string
  edgeRadius: number
  lastRoundedRadius: number
  globalSettings: {
    default_java_path: string | null
    auto_install_java: boolean
    show_snapshots: boolean
    auto_update_enabled: boolean
  }
}

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

  const dark = state.theme === 'dark'
  const bgPrimary = dark ? '#0d0d0d' : '#f5f5f5'
  const bgSecondary = dark ? '#1a1a1a' : '#ffffff'
  const bgTertiary = dark ? '#262626' : '#e0e0e0'
  const textPrimary = dark ? '#ffffff' : '#1a1a1a'
  const textSecondary = dark ? '#b3b3b3' : '#666666'
  const borderColor = dark ? '#404040' : '#cccccc'
  const shadowColor = dark ? 'rgba(0, 0, 0, 0.5)' : 'rgba(0, 0, 0, 0.1)'

  const accent = state.accentColor
  const accentHover = darken(accent, 0.1)
  const accentText = getContrastingText(accent)
  const edgeRadius = `${state.edgeRadius}px`

  root.style.setProperty('--bg-primary', bgPrimary)
  root.style.setProperty('--bg-secondary', bgSecondary)
  root.style.setProperty('--bg-tertiary', bgTertiary)
  root.style.setProperty('--text-primary', textPrimary)
  root.style.setProperty('--text-secondary', textSecondary)
  root.style.setProperty('--border-color', borderColor)
  root.style.setProperty('--shadow-color', shadowColor)
  root.style.setProperty('--accent', accent)
  root.style.setProperty('--accent-hover', accentHover)
  root.style.setProperty('--accent-text', accentText)
  root.style.setProperty('--edge-radius', edgeRadius)
}

const STORAGE_KEY = 'pretupid-settings'

function defaultSettings(): SettingsState {
  return {
    theme: 'dark',
    accentColor: '#4a9eff',
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

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<SettingsState>(loadSettingsFromStorage())

  watch(
    settings,
    (newSettings) => {
      applySettings(newSettings)
      localStorage.setItem(STORAGE_KEY, JSON.stringify(newSettings))
    },
    { deep: true, immediate: true }
  )

  function setTheme(theme: Theme) {
    settings.value.theme = theme
  }

  function setAccentColor(color: string) {
    settings.value.accentColor = color
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
    setEdgeStyle,
    setEdgeRadius,
    setGlobalSettings,
    loadLocalSettings,
    loadGlobalSettings,
    resetGlobalSettings,
    resetToDefaults
  }
})