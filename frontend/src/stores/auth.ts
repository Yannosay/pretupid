import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAuthStatus, loginMicrosoft, logoutMicrosoft } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const authenticated = ref(false)
  const profile = ref<{ id: string; name: string } | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function check() {
    console.log('check() called')
    loading.value = true
    error.value = null
    try {
      const status = await getAuthStatus()
      console.log('getAuthStatus returned:', status)
      authenticated.value = status.authenticated
      profile.value = status.profile
      console.log('State updated:', authenticated.value, profile.value)
    } catch (e) {
      error.value = String(e)
      console.error('check() error:', e)
    } finally {
      loading.value = false
    }
  }

  async function login() {
    console.log('login() called')
    loading.value = true
    error.value = null
    try {
      await loginMicrosoft()
      console.log('loginMicrosoft returned')
    } catch (e) {
      error.value = String(e)
      console.error('login() error:', e)
      loading.value = false
    }
  }

  async function logout() {
    console.log('logout() called')
    loading.value = true
    error.value = null
    try {
      await logoutMicrosoft()
      authenticated.value = false
      profile.value = null
    } catch (e) {
      error.value = String(e)
      console.error('logout() error:', e)
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