import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { emit, listen } from '@tauri-apps/api/event'
import type { Instance, InstanceMod, UpdateInstanceRequest } from '@/types'
import * as api from '@/api'

export const useInstanceStore = defineStore('instances', () => {
  const instances = ref<Instance[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const instanceMods = ref<Record<string, InstanceMod[]>>({})
  let listening = false

  async function broadcastChange() {
    try {
      await emit('instances://changed')
    } catch {
      void 0
    }
  }

  async function ensureListening() {
    if (listening) return
    listening = true
    try {
      await listen('instances://changed', () => {
        load(true).catch(() => void 0)
      })
    } catch {
      listening = false
    }
  }

  async function load(silent = false) {
    if (loading.value) return
    if (!silent) loading.value = true
    error.value = null
    ensureListening().catch(() => void 0)
    try {
      const result = await api.listInstances()
      instances.value = Array.isArray(result) ? result : []
      loaded.value = true
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }
  }

  async function create(request: Parameters<typeof api.createInstance>[0]) {
    error.value = null
    const instance = await api.createInstance(request)
    await load(true)
    await broadcastChange()
    return instance
  }

  async function update(id: string, request: UpdateInstanceRequest) {
    const updated = await api.updateInstance(id, request)
    if (updated) {
      instances.value = instances.value.map((i) => (i.id === id ? updated : i))
    }
    await broadcastChange()
    return updated
  }

  async function duplicate(id: string, name?: string) {
    const created = await api.duplicateInstance(id, name)
    await load(true)
    await broadcastChange()
    return created
  }

  async function remove(id: string) {
    await api.deleteInstance(id)
    instances.value = instances.value.filter((i) => i.id !== id)
    await broadcastChange()
  }

  async function loadMods(instanceId: string): Promise<InstanceMod[]> {
    const mods = await api.listInstanceMods(instanceId)
    instanceMods.value[instanceId] = mods
    return mods
  }

  function byId(id: string): Instance | undefined {
    return instances.value.find((i) => i.id === id)
  }

  const recent = computed(() =>
    [...instances.value].sort((a, b) => {
      const at = new Date(a.last_played ?? a.created_at).getTime()
      const bt = new Date(b.last_played ?? b.created_at).getTime()
      return bt - at
    })
  )

  const groups = computed(() => {
    const set = new Set<string>()
    for (const i of instances.value) if (i.group_name) set.add(i.group_name)
    return [...set].sort((a, b) => a.localeCompare(b))
  })

  return {
    instances,
    loading,
    loaded,
    error,
    instanceMods,
    recent,
    groups,
    load,
    create,
    update,
    duplicate,
    remove,
    loadMods,
    byId
  }
})
