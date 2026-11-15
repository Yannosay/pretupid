import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as profileApi from '@/api/profile'
import type { SkinSlotDto, SkinModelType } from '@/types/profile'

interface CurrentSkinData {
  preview_path: string
  model_type: SkinModelType
  data_url: string
}

export const useProfileStore = defineStore('profile', () => {
  const profileId = ref('')
  const skinSlots = ref<SkinSlotDto[]>([])
  const currentSkin = ref<CurrentSkinData | null>(null)
  const loading = ref(false)
  const importing = ref(false)
  const exporting = ref(false)
  const applying = ref(false)
  const error = ref<string | null>(null)
  const success = ref<string | null>(null)

  const activeSlot = computed(
    () => skinSlots.value.find((slot) => slot.is_active) ?? skinSlots.value[0] ?? null
  )

  const previewUrl = computed(() => {
    const slot = activeSlot.value
    return slot ? (slot.data_url || '') : ''
  })

  const currentPreviewUrl = computed(() => {
    return currentSkin.value ? (currentSkin.value.data_url || '') : ''
  })

  const currentSkinDataUrl = computed(() => {
    return currentSkin.value?.data_url ?? ''
  })

  const currentModelType = computed(() => {
    return currentSkin.value?.model_type ?? 'classic'
  })

  function nextSlotNumber(): number {
    const numbers = skinSlots.value.map((slot) => slot.slot_number)
    if (numbers.length === 0) return 1
    return Math.max(...numbers) + 1
  }

  function fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => {
        const result = reader.result as string | null
        if (!result) {
          reject(new Error('Unable to read skin file.'))
          return
        }

        const comma = result.indexOf(',')
        const base64 = comma >= 0 ? result.slice(comma + 1) : result
        resolve(base64)
      }
      reader.onerror = () => reject(new Error('Unable to read skin file.'))
      reader.readAsDataURL(file)
    })
  }

  async function load(id: string) {
    profileId.value = id
    loading.value = true
    error.value = null

    try {
      skinSlots.value = await profileApi.listSkinSlots(id)
    } catch (e) {
      error.value = String(e)
    } finally {
      loading.value = false
    }

    try {
      currentSkin.value = await profileApi.getCurrentSkin(id)
    } catch {
      currentSkin.value = null
    }
  }

  async function importFromFile(file: File, name: string, modelType: SkinModelType) {
    if (!profileId.value) return
    if (skinSlots.value.length >= 5) {
      error.value = 'Skin slot limit reached.'
      return
    }

    importing.value = true
    error.value = null
    success.value = null

    try {
      const base64 = await fileToBase64(file)
      await profileApi.importSkinFromFile(
        profileId.value,
        nextSlotNumber(),
        name,
        modelType,
        file.name,
        base64
      )
      await load(profileId.value)
      success.value = 'Skin imported successfully.'
    } catch (e) {
      error.value = String(e)
    } finally {
      importing.value = false
    }
  }

  async function importFromUrl(url: string, name: string, modelType: SkinModelType) {
    if (!profileId.value) return
    if (skinSlots.value.length >= 5) {
      error.value = 'Skin slot limit reached.'
      return
    }

    importing.value = true
    error.value = null
    success.value = null

    try {
      await profileApi.importSkinFromUrl(
        profileId.value,
        nextSlotNumber(),
        name,
        modelType,
        url
      )
      await load(profileId.value)
      success.value = 'Skin imported successfully.'
    } catch (e) {
      error.value = String(e)
    } finally {
      importing.value = false
    }
  }

  async function importFromUsername(username: string, name: string): Promise<boolean> {
    if (!profileId.value) return false
    if (skinSlots.value.length >= 5) {
      error.value = 'Skin slot limit reached.'
      return false
    }

    importing.value = true
    error.value = null
    success.value = null

    try {
      await profileApi.importSkinFromUsername(profileId.value, nextSlotNumber(), name, username)
      await load(profileId.value)
      success.value = 'Skin imported successfully.'
      return true
    } catch (e) {
      error.value = String(e)
      return false
    } finally {
      importing.value = false
    }
  }

  async function removeSlot(slotId: string) {
    if (!profileId.value) return
    error.value = null

    try {
      await profileApi.deleteSkinSlot(slotId)
      await load(profileId.value)
      success.value = 'Skin slot removed.'
    } catch (e) {
      error.value = String(e)
    }
  }

  async function setActive(slotId: string) {
    if (!profileId.value) return
    error.value = null

    try {
      await profileApi.setActiveSkinSlot(profileId.value, slotId)
      await load(profileId.value)
    } catch (e) {
      error.value = String(e)
    }
  }

  async function reorder(orderedSlotIds: string[]) {
    if (!profileId.value) return
    error.value = null

    try {
      await profileApi.reorderSkinSlots(profileId.value, orderedSlotIds)
      await load(profileId.value)
    } catch (e) {
      error.value = String(e)
    }
  }

  async function applyActive() {
    const slot = activeSlot.value
    if (!slot) return

    applying.value = true
    error.value = null
    success.value = null

    try {
      await profileApi.applySkinToMinecraft(slot.id)
      success.value = 'Skin applied to Minecraft account.'
    } catch (e) {
      error.value = String(e)
    } finally {
      applying.value = false
    }
  }

  async function exportSlotToDownloads(slotId: string): Promise<string | null> {
    exporting.value = true
    error.value = null
    try {
      return await profileApi.exportSkinSlotToDownloads(slotId)
    } catch (e) {
      error.value = String(e)
      return null
    } finally {
      exporting.value = false
    }
  }

  async function exportSlot(slotId: string) {
    exporting.value = true
    error.value = null

    try {
      return await profileApi.exportSkinSlot(slotId)
    } catch (e) {
      error.value = String(e)
      return null
    } finally {
      exporting.value = false
    }
  }

  return {
    profileId,
    skinSlots,
    currentSkin,
    loading,
    importing,
    exporting,
    applying,
    error,
    success,
    activeSlot,
    previewUrl,
    currentPreviewUrl,
    currentSkinDataUrl,
    currentModelType,
    load,
    importFromFile,
    importFromUrl,
    importFromUsername,
    removeSlot,
    setActive,
    reorder,
    applyActive,
    exportSlot,
    exportSlotToDownloads,
    nextSlotNumber
  }
})