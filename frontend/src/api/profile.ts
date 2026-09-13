import { invoke } from '@tauri-apps/api/core'
import type { SkinSlotDto, SkinModelType } from '@/types/profile'

export async function listSkinSlots(profileId: string): Promise<SkinSlotDto[]> {
  return invoke('list_skin_slots', { profileId })
}

export async function saveSkinSlot(
  slotId: string,
  name: string,
  modelType: SkinModelType,
  source: string,
  fileName: string | null,
  originalFileName: string | null
): Promise<SkinSlotDto> {
  return invoke('save_skin_slot', {
    slotId,
    name,
    modelType,
    source,
    fileName,
    originalFileName
  })
}

export async function deleteSkinSlot(slotId: string): Promise<boolean> {
  return invoke('delete_skin_slot', { slotId })
}

export async function setActiveSkinSlot(profileId: string, slotId: string): Promise<void> {
  return invoke('set_active_skin_slot', { profileId, slotId })
}

export async function importSkinFromFile(
  profileId: string,
  slotNumber: number,
  name: string,
  modelType: SkinModelType,
  originalFileName: string | null,
  fileDataBase64: string
): Promise<SkinSlotDto> {
  return invoke('import_skin_from_file', {
    profileId,
    slotNumber,
    name,
    modelType,
    originalFileName,
    fileDataBase64
  })
}

export async function importSkinFromUrl(
  profileId: string,
  slotNumber: number,
  name: string,
  modelType: SkinModelType,
  url: string
): Promise<SkinSlotDto> {
  return invoke('import_skin_from_url', {
    profileId,
    slotNumber,
    name,
    modelType,
    url
  })
}

export async function applySkinToMinecraft(slotId: string): Promise<void> {
  return invoke('apply_skin_slot', { slotId })
}

export async function exportSkinSlot(slotId: string): Promise<string> {
  return invoke('export_skin_slot', { slotId })
}

export async function reorderSkinSlots(profileId: string, orderedSlotIds: string[]): Promise<void> {
  return invoke('reorder_skin_slots', { profileId, orderedSlotIds })
}

export interface CurrentSkinDto {
  preview_path: string
  model_type: SkinModelType
  data_url: string
}

export async function getCurrentSkin(profileId: string): Promise<CurrentSkinDto> {
  return invoke('get_current_skin', { profileId })
}