import { invoke } from '@tauri-apps/api/core'
import type {
  ModrinthSearchResponse,
  ModrinthProject,
  ModrinthVersion,
  ModrinthCategory,
  ModrinthLoader,
  ModrinthGameVersion
} from '@/types'

export async function searchModrinth(
  query: string,
  facets?: string,
  index?: string,
  offset?: number,
  limit?: number
): Promise<ModrinthSearchResponse> {
  return invoke('search_modrinth', { query, facets, index, offset, limit })
}

export async function getModrinthProject(idOrSlug: string): Promise<ModrinthProject> {
  return invoke('get_modrinth_project', { idOrSlug })
}

export async function getModrinthVersions(idOrSlug: string): Promise<ModrinthVersion[]> {
  return invoke('get_modrinth_versions', { idOrSlug })
}

export async function getModrinthCategories(): Promise<ModrinthCategory[]> {
  return invoke('get_modrinth_categories')
}

export async function getModrinthLoaders(): Promise<ModrinthLoader[]> {
  return invoke('get_modrinth_loaders')
}

export async function getModrinthGameVersions(): Promise<ModrinthGameVersion[]> {
  return invoke('get_modrinth_game_versions')
}