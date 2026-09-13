import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAuthStatus, loginMicrosoft, logoutMicrosoft } from '@/api'

const CACHE_KEY = 'pretupid-auth-cache'

interface AuthProfile {
  id: string
  name: string
}

interface AuthCache {
  authenticated: boolean
  profile: AuthProfile | null
}

function readCache(): AuthCache | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthCache
    if (typeof parsed.authenticated !== 'boolean') return null
    return parsed
  } catch {
    return null
  }
}

function writeCache(value: AuthCache) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(value))
  } catch {
    void 0
  }
}

function clearCache() {
  try {
    localStorage.removeItem(CACHE_KEY)
  } catch {
    void 0
  }
}

export const useAuthStore = defineStore('auth', () => {
  const cached = readCache()
  const authenticated = ref<boolean>(cached?.authenticated ?? false)
  const profile = ref<AuthProfile | null>(cached?.profile ?? null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function check() {
    loading.value = true
    error.value = null
    try {
      const status = await getAuthStatus()
      authenticated.value = status.authenticated
      profile.value = status.profile
      writeCache({ authenticated: status.authenticated, profile: status.profile })
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function login() {
    loading.value = true
    error.value = null
    try {
      await loginMicrosoft()
    } catch (e) {
      error.value = String(e)
      loading.value = false
    }
  }

  async function logout() {
    loading.value = true
    error.value = null
    try {
      await logoutMicrosoft()
      authenticated.value = false
      profile.value = null
      clearCache()
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  return {
    authenticated,
    profile,
    loading,
    error,
    check,
    login,
    logout
  }
})