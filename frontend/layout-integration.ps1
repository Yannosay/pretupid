$ErrorActionPreference = "Stop"

function Write-File {
    param(
        [string]$Path,
        [string]$Content
    )
    $Directory = Split-Path -Parent $Path
    if ($Directory -and -not (Test-Path $Directory)) {
        New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    }
    Set-Content -Path $Path -Value $Content -NoNewline -Encoding UTF8
}

$appLayout = @'
<template>
  <div class="app-layout">
    <SidebarNav @open-settings="settingsModalOpen = true" />
    <main class="app-layout__content">
      <RouterView />
    </main>
    <SettingsModal v-model="settingsModalOpen" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import SidebarNav from '@/components/SidebarNav.vue'
import SettingsModal from '@/components/SettingsModal.vue'

const settingsModalOpen = ref(false)
</script>

<style scoped lang="scss">
.app-layout {
  display: flex;
  min-height: 100vh;
}

.app-layout__content {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
}
</style>
'@
Write-File -Path "src/components/AppLayout.vue" -Content $appLayout

$sidebarNav = @'
<template>
  <aside class="sidebar">
    <div class="sidebar__logo">
      <img src="@/assets/branding/ptd_logo_main.webp" alt="Pretupid Launcher" />
      <span class="sidebar__logo-text">Pretupid</span>
    </div>

    <nav class="sidebar__nav">
      <RouterLink to="/" class="sidebar__nav-link" active-class="sidebar__nav-link--active">
        Library
      </RouterLink>
      <RouterLink to="/settings" class="sidebar__nav-link" active-class="sidebar__nav-link--active">
        Settings
      </RouterLink>
    </nav>

    <div class="sidebar__actions">
      <button class="btn btn--ghost" @click="$emit('open-settings')">
        Customize
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineEmits<{
  (e: 'open-settings'): void
}>()
</script>

<style scoped lang="scss">
.sidebar {
  width: 240px;
  background-color: var(--bg-secondary);
  border-right: 2px solid var(--border-color);
  display: flex;
  flex-direction: column;
  padding: 1.5rem 1rem;
}

.sidebar__logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.sidebar__logo img {
  width: 40px;
  height: 40px;
}

.sidebar__logo-text {
  font-family: var(--font-pixel);
  font-size: 1.2rem;
  color: var(--accent);
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sidebar__nav-link {
  padding: 0.75rem 1rem;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  text-transform: uppercase;
  color: var(--text-primary);
  border: 2px solid transparent;
  border-radius: var(--edge-radius);
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
}

.sidebar__nav-link:hover {
  background-color: var(--bg-tertiary);
}

.sidebar__nav-link--active {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}

.sidebar__actions {
  margin-top: auto;
}
</style>
'@
Write-File -Path "src/components/SidebarNav.vue" -Content $sidebarNav

$settingsModal = @'
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
              class="btn"
              :class="{ 'btn--active': settings.theme === 'dark' }"
              @click="settingsStore.setTheme('dark')"
            >
              Dark
            </button>
            <button
              class="btn"
              :class="{ 'btn--active': settings.theme === 'light' }"
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
              @input="settingsStore.setAccentColor($event.target.value)"
            />
            <span>Custom</span>
          </div>
        </section>

        <section class="modal__section">
          <h3>Edge Style</h3>
          <div class="edge-toggle">
            <button
              class="btn"
              :class="{ 'btn--active': settings.edgeStyle === 'blocky' }"
              @click="settingsStore.setEdgeStyle('blocky')"
            >
              Blocky
            </button>
            <button
              class="btn"
              :class="{ 'btn--active': settings.edgeStyle === 'rounded' }"
              @click="settingsStore.setEdgeStyle('rounded')"
            >
              Rounded
            </button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useSettingsStore } from '@/stores/settings'
import { computed } from 'vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const settingsStore = useSettingsStore()
const settings = computed(() => settingsStore.settings)

const presetColors = ['#ffffff', '#4aff9e', '#5a9eff', '#ff9eb5', '#b47aff']

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
  max-width: 500px;
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
  font-size: 1.5rem;
}

.modal__close {
  font-size: 2rem;
  line-height: 1;
  color: var(--text-secondary);
  transition: color var(--transition-fast);
}

.modal__close:hover {
  color: var(--text-primary);
}

.modal__section {
  margin-bottom: 1.5rem;
}

.modal__section h3 {
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
}

.theme-toggle,
.edge-toggle {
  display: flex;
  gap: 0.5rem;
}

.btn--active {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}

.color-presets {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
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
</style>
'@
Write-File -Path "src/components/SettingsModal.vue" -Content $settingsModal

$appVue = @'
<script setup lang="ts">
import AppLayout from '@/components/AppLayout.vue'
</script>

<template>
  <AppLayout />
</template>
'@
Write-File -Path "src/App.vue" -Content $appVue

$libraryView = @'
<template>
  <div class="container">
    <h1>Library</h1>
    <p class="text-secondary">Your Minecraft instances will appear here.</p>
  </div>
</template>

<script setup lang="ts">
</script>
'@
Write-File -Path "src/views/LibraryView.vue" -Content $libraryView

$instanceDetailView = @'
<template>
  <div class="container">
    <h1>Instance Detail</h1>
    <p class="text-secondary">Instance details will appear here.</p>
  </div>
</template>

<script setup lang="ts">
</script>
'@
Write-File -Path "src/views/InstanceDetailView.vue" -Content $instanceDetailView

$settingsView = @'
<template>
  <div class="container">
    <h1>Settings</h1>
    <p class="text-secondary">Settings are available via the sidebar button.</p>
  </div>
</template>

<script setup lang="ts">
</script>
'@
Write-File -Path "src/views/SettingsView.vue" -Content $settingsView

$viteConfig = @'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true
  }
})
'@
Write-File -Path "vite.config.ts" -Content $viteConfig

$tsconfig = @'
{
  "compilerOptions": {
    "target": "ESNext",
    "module": "ESNext",
    "moduleResolution": "Node",
    "strict": true,
    "jsx": "preserve",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "esModuleInterop": true,
    "lib": ["ESNext", "DOM"],
    "skipLibCheck": true,
    "noEmit": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.tsx", "src/**/*.vue"]
}
'@
Write-File -Path "tsconfig.json" -Content $tsconfig

Write-Host "Layout files created successfully."