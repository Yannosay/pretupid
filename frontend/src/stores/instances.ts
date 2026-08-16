import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Instance } from '@/types'
import {
  createInstance as apiCreateInstance,
  deleteInstance as apiDeleteInstance,
  listInstances
} from '@/api'

export const useInstanceStore = defineStore('instances', () => {
  const instances = ref<Instance[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function load() {
    if (loading.value) return
    loading.value = true
    error.value = null
    try {
      instances.value = await listInstances()
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function create(request: Parameters<typeof apiCreateInstance>[0]) {
    error.value = null
    try {
      const instance = await apiCreateInstance(request)
      await load()
      return instance
    } catch (e) {
      error.value = String(e)
      throw e
    }
  }

  async function remove(id: string) {
    error.value = null
    try {
      await apiDeleteInstance(id)
      await load()
    } catch (e) {
      error.value = String(e)
      throw e
    }
  }

  return {
    instances,
    loading,
    error,
    load,
    create,
    remove
  }
})