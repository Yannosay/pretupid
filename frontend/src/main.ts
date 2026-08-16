import '@fontsource/press-start-2p'
import '@fontsource/vt323'
import './assets/styles/main.scss'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { listen } from '@tauri-apps/api/event'
import { useAuthStore } from '@/stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const authStore = useAuthStore()

await listen('auth-state', async () => {
  console.log('Store listener fired')
  await authStore.check()
})

await authStore.check()

app.mount('#app')