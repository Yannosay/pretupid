<template>
  <div class="tray-panel">
    <header class="tray-panel__header">
      <div class="tray-panel__brand">
        <img
          src="@shared/branding/logo.png"
          alt="Pretupid"
          class="tray-panel__brand-logo"
        />
        <div class="tray-panel__brand-text">
          <span class="tray-panel__brand-main">Pretupid</span>
          <span class="tray-panel__brand-sub">Launcher</span>
        </div>
      </div>
      <button
        type="button"
        class="tray-panel__close"
        aria-label="Close panel"
        @click="closePanel"
      >
        ×
      </button>
    </header>

    <div class="tray-panel__body">
      <div v-if="loading" class="tray-panel__state">
        <span class="pixel-loader">
          Loading
          <span class="pixel-loader__dots">
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
            <span class="pixel-loader__dot"></span>
          </span>
        </span>
      </div>

      <div v-else-if="error" class="tray-panel__state tray-panel__state--error">
        {{ error }}
      </div>

      <template v-else>
        <div class="tray-panel__section">
          <span class="tray-panel__section-label">Instances</span>

          <div v-if="instances.length === 0" class="tray-panel__empty">
            <p class="tray-panel__empty-title">No instances yet</p>
            <p class="tray-panel__empty-hint">Create one in the main launcher.</p>
          </div>

          <ul v-else class="tray-panel__list">
            <li v-for="instance in instances" :key="instance.id">
              <button type="button" class="tray-panel__instance" @click="launch(instance)">
                <span class="tray-panel__instance-icon" aria-hidden="true">
                  {{ instanceInitial(instance) }}
                </span>
                <span class="tray-panel__instance-body">
                  <span class="tray-panel__instance-name">{{ instance.name }}</span>
                  <span class="tray-panel__instance-meta">
                    {{ instance.minecraft_version }} · {{ instance.mod_loader }}
                  </span>
                </span>
              </button>
            </li>
          </ul>
        </div>

        <div class="tray-panel__section">
          <span class="tray-panel__section-label">Quick links</span>
          <ul class="tray-panel__nav">
            <li>
              <button type="button" class="tray-panel__nav-item" @click="openRoute('/')">
                <span class="tray-panel__nav-dot" aria-hidden="true"></span>
                Home
              </button>
            </li>
            <li>
              <button type="button" class="tray-panel__nav-item" @click="openRoute('/library')">
                <span class="tray-panel__nav-dot" aria-hidden="true"></span>
                Instances
              </button>
            </li>
            <li>
              <button type="button" class="tray-panel__nav-item" @click="openRoute('/profile')">
                <span class="tray-panel__nav-dot" aria-hidden="true"></span>
                Profile
              </button>
            </li>
            <li>
              <button type="button" class="tray-panel__nav-item" @click="openRoute('/browse')">
                <span class="tray-panel__nav-dot" aria-hidden="true"></span>
                Modrinth Library
              </button>
            </li>
          </ul>
        </div>
      </template>
    </div>

    <footer class="tray-panel__footer">
      <button type="button" class="tray-panel__footer-btn" @click="openMain">
        Open Launcher
      </button>
      <button
        type="button"
        class="tray-panel__footer-btn tray-panel__footer-btn--danger"
        @click="quit"
      >
        Quit
      </button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import { useSettingsStore } from '@/stores/settings'
import type { Instance } from '@/types'

const settingsStore = useSettingsStore()
settingsStore.loadLocalSettings()

const instances = ref<Instance[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const unlisteners: UnlistenFn[] = []

function instanceInitial(instance: Instance): string {
  if (!instance || typeof instance.name !== 'string') return '?'
  const trimmed = instance.name.trim()
  if (trimmed.length === 0) return '?'
  return trimmed.charAt(0).toUpperCase()
}

async function loadInstances() {
  loading.value = true
  error.value = null
  try {
    const result = await invoke<Instance[]>('list_instances')
    instances.value = Array.isArray(result) ? result : []
  } catch (e) {
    error.value = String(e)
  } finally {
    loading.value = false
  }
}

async function closePanel() {
  try {
    await invoke('hide_tray_panel')
  } catch (e) {
    error.value = String(e)
  }
}

async function openMain() {
  try {
    await invoke('open_main_window', { route: null })
  } catch (e) {
    error.value = String(e)
  }
}

async function openRoute(route: string) {
  try {
    await invoke('open_main_window', { route })
  } catch (e) {
    error.value = String(e)
  }
}

async function quit() {
  try {
    await invoke('quit_app')
  } catch (e) {
    error.value = String(e)
  }
}

async function launch(instance: Instance) {
  if (!instance || typeof instance.id !== 'string') return
  try {
    await invoke('request_launch_from_tray', { instanceId: instance.id })
  } catch (e) {
    error.value = String(e)
  }
}

onMounted(async () => {
  await loadInstances()

  unlisteners.push(
    await listen('instances://changed', () => {
      loadInstances().catch(() => void 0)
    })
  )

  unlisteners.push(
    await listen<unknown>('settings://changed', (event) => {
      const payload = event.payload
      if (payload && typeof payload === 'object') {
        settingsStore.applyExternalSettings(
          payload as Parameters<typeof settingsStore.applyExternalSettings>[0]
        )
      }
    })
  )
})

onBeforeUnmount(() => {
  for (const unlisten of unlisteners) {
    try {
      unlisten()
    } catch {
      void 0
    }
  }
  unlisteners.length = 0
})
</script>

<style scoped lang="scss">
.tray-panel {
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  box-sizing: border-box;
  overflow: hidden;
  color: var(--text-primary);
}

.tray-panel__header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.tray-panel__brand {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.tray-panel__brand-logo {
  width: 28px;
  height: 28px;
  border-radius: var(--edge-radius);
}

.tray-panel__brand-text {
  display: flex;
  flex-direction: column;
  gap: 0.05rem;
  min-width: 0;
}

.tray-panel__brand-main {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--accent);
  letter-spacing: 0.03em;
}

.tray-panel__brand-sub {
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.tray-panel__close {
  font-size: 1.4rem;
  line-height: 1;
  padding: 0.1rem 0.4rem;
  color: var(--text-secondary);
  background: none;
  border: 1px solid transparent;
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: color var(--transition-fast), border-color var(--transition-fast);
}

.tray-panel__close:hover {
  color: var(--text-primary);
  border-color: var(--border-color);
}

.tray-panel__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0.75rem 0.5rem 0.5rem 0.5rem;
}

.tray-panel__state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 0.35rem;
  height: 100%;
  text-align: center;
  padding: 1.5rem 1rem;
  color: var(--text-secondary);
}

.tray-panel__state--error {
  color: var(--danger);
}

.tray-panel__section {
  margin-bottom: 0.75rem;
}

.tray-panel__section:last-child {
  margin-bottom: 0;
}

.tray-panel__section-label {
  display: block;
  padding: 0 0.5rem 0.4rem;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
}

.tray-panel__empty {
  padding: 1rem 0.75rem;
  text-align: center;
  border: 1px dashed var(--border-color);
  border-radius: var(--edge-radius);
  margin: 0 0.25rem;
}

.tray-panel__empty-title {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-primary);
  margin: 0 0 0.25rem;
}

.tray-panel__empty-hint {
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin: 0;
}

.tray-panel__list,
.tray-panel__nav {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.tray-panel__instance {
  width: 100%;
  padding: 0.55rem 0.6rem;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  background-color: transparent;
  border: 1px solid transparent;
  border-radius: var(--edge-radius);
  cursor: pointer;
  text-align: left;
  color: var(--text-primary);
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
}

.tray-panel__instance:hover {
  background-color: var(--bg-tertiary);
  border-color: var(--border-color);
}

.tray-panel__instance-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--accent-text);
  background-color: var(--accent);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.tray-panel__instance-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.tray-panel__instance-name {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tray-panel__instance-meta {
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.tray-panel__nav-item {
  width: 100%;
  padding: 0.5rem 0.6rem;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  background-color: transparent;
  border: 1px solid transparent;
  border-radius: var(--edge-radius);
  cursor: pointer;
  text-align: left;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 1rem;
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
}

.tray-panel__nav-item:hover {
  background-color: var(--bg-tertiary);
  border-color: var(--border-color);
}

.tray-panel__nav-dot {
  width: 8px;
  height: 8px;
  background-color: var(--accent);
  border-radius: var(--edge-radius);
  flex-shrink: 0;
}

.tray-panel__footer {
  display: flex;
  gap: 0.4rem;
  padding: 0.6rem 0.5rem;
  border-top: 1px solid var(--border-color);
  background-color: var(--bg-tertiary);
  flex-shrink: 0;
}

.tray-panel__footer-btn {
  flex: 1;
  padding: 0.55rem 0.6rem;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  letter-spacing: 0.05em;
  color: var(--text-primary);
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast);
}

.tray-panel__footer-btn:hover {
  border-color: var(--accent);
}

.tray-panel__footer-btn--danger {
  color: #ffffff;
  background-color: var(--danger);
  border-color: var(--danger);
}

.tray-panel__footer-btn--danger:hover {
  background-color: var(--danger);
  border-color: #a93226;
}
</style>