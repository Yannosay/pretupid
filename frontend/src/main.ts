import './assets/styles/main.scss'
import logoUrl from '@shared/branding/logo.png'
import { createApp, type App as VueApp } from 'vue'
import { createPinia } from 'pinia'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { listen } from '@tauri-apps/api/event'

const favicon = document.createElement('link')
favicon.rel = 'icon'
favicon.type = 'image/png'
favicon.href = logoUrl
document.head.appendChild(favicon)

const currentWindow = getCurrentWindow()
const isTrayPanel = currentWindow.label === 'tray-panel'

function installGlobalErrorHandler(app: VueApp) {
  app.config.errorHandler = (err, _instance, info) => {
    const message = err instanceof Error ? err.message : String(err)
    const stack = err instanceof Error ? err.stack ?? '' : ''
    console.error('[pretupid] Vue error:', message, info, stack)
  }
}

if (isTrayPanel) {
  const bootSplash = document.getElementById('boot-splash')
  if (bootSplash) bootSplash.remove()

  Promise.all([
    import('./TrayPanel.vue'),
    import('@/stores/settings')
  ]).then(([{ default: TrayPanel }, { useSettingsStore }]) => {
    const app = createApp(TrayPanel)
    installGlobalErrorHandler(app)
    app.use(createPinia())

    const settingsStore = useSettingsStore()
    settingsStore.loadLocalSettings()

    app.mount('#app')
  })
} else {
  Promise.all([
    import('./App.vue'),
    import('./router')
  ]).then(
    ([{ default: App }, { default: router, registerNavigationListeners }]) => {
      const app = createApp(App)
      installGlobalErrorHandler(app)
      app.use(createPinia())
      app.use(router)
      app.mount('#app')

      const bootSplash = document.getElementById('boot-splash')
      if (bootSplash) {
        requestAnimationFrame(() => {
          bootSplash.classList.add('boot-splash--hidden')
          setTimeout(() => bootSplash.remove(), 400)
        })
      }

      registerNavigationListeners().catch(() => void 0)

      import('@/stores/auth').then(({ useAuthStore }) => {
        const authStore = useAuthStore()
        listen('auth-state', async () => {
          await authStore.check()
        })
      })
    }
  )
}