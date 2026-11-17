export type SkinModelType = 'classic' | 'slim'
export type SkinSource = 'mojang' | 'local_file' | 'url'

export interface SkinSlotDto {
  id: string
  profile_id: string
  slot_number: number
  name: string
  file_name: string
  original_file_name: string | null
  model_type: SkinModelType
  source: SkinSource
  is_active: boolean
  created_at: string
  updated_at: string
  preview_path: string
  data_url: string
}

export interface MinecraftCapeDto {
  id: string
  alias: string
  active: boolean
  data_url: string | null
}
