import { defineStore } from 'pinia'
import { ref } from 'vue'
import { emit } from '@tauri-apps/api/event'
import type { Instance, InstanceMod } from '@/types'
import * as api from '@/api'

export const useInstanceStore = defineStore('instances', () => {
  const instances = ref<Instance[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const instanceMods = ref<Record<string, InstanceMod[]>>({})

  async function broadcastChange() {
    try {
      await emit('instances://changed')
    } catch {
      void 0
    }
  }

  async function load() {
    if (loading.value) return
    loading.value = true
    error.value = null
    try {
      const result = await api.listInstances()
      instances.value = Array.isArray(result) ? result : []
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function create(request: Parameters<typeof api.createInstance>[0]) {
    error.value = null
    try {
      const instance = await api.createInstance(request)
      await load()
      await broadcastChange()
      return instance
    } catch (e) {
      error.value = String(e)
      throw e
    }
  }

  async function remove(id: string) {
    error.value = null
    try {
      await api.deleteInstance(id)
      await load()
      await broadcastChange()
    } catch (e) {
      error.value = String(e)
      throw e
    }
  }

  async function loadMods(instanceId: string): Promise<InstanceMod[]> {
    try {
      const mods = await api.listInstanceMods(instanceId)
      instanceMods.value[instanceId] = mods
      return mods
    } catch (e) {
      error.value = String(e)
      return []
    }
  }

  async function addMod(instanceId: string, mod: InstanceMod) {
    if (!instanceMods.value[instanceId]) {
      instanceMods.value[instanceId] = []
    }
    instanceMods.value[instanceId].push(mod)
  }

  async function removeMod(instanceId: string, modId: string) {
    if (instanceMods.value[instanceId]) {
      instanceMods.value[instanceId] = instanceMods.value[instanceId].filter(
        (m) => m.id !== modId
      )
    }
  }

  return {
    instances,
    loading,
    error,
    instanceMods,
    load,
    create,
    remove,
    loadMods,
    addMod,
    removeMod
  }
})