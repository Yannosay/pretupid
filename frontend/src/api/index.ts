import { invoke } from '@tauri-apps/api/core'
import type {
  InstanceMod,
  CreateInstanceRequest,
  GlobalSettings,
  Instance,
  InstanceSettings,
  LaunchSettings,
  VersionEntry
} from '@/types'

export {
  searchModrinth,
  getModrinthProject,
  getModrinthVersions,
  getModrinthCategories,
  getModrinthLoaders,
  getModrinthGameVersions
} from '@/api/modrinth'

export async function listInstances(): Promise<Instance[]> {
  return invoke('list_instances')
}

export async function createInstance(request: CreateInstanceRequest): Promise<Instance> {
  return invoke('create_instance', {
    name: request.name,
    description: request.description,
    minecraftVersion: request.minecraft_version,
    modLoader: request.mod_loader,
    loaderVersion: request.loader_version
  })
}

export async function getInstance(id: string): Promise<Instance | null> {
  return invoke('get_instance', { id })
}

export async function deleteInstance(id: string): Promise<boolean> {
  return invoke('delete_instance', { id })
}

export async function fetchVersions(): Promise<VersionEntry[]> {
  return invoke('fetch_versions')
}

export async function launchInstance(
  instanceId: string,
  versionId: string,
  settings: LaunchSettings
): Promise<void> {
  return invoke('launch_instance', {
    instanceId,
    versionId,
    settings
  })
}

export async function saveInstanceSettings(
  id: string,
  settings: InstanceSettings
): Promise<void> {
  return invoke('save_instance_settings', { id, settings })
}

export async function getInstanceSettings(id: string): Promise<InstanceSettings> {
  return invoke('get_instance_settings', { id })
}

export async function loginMicrosoft(): Promise<void> {
  return invoke('login_microsoft')
}

export async function logoutMicrosoft(): Promise<void> {
  return invoke('logout_microsoft')
}

export async function getAuthStatus(): Promise<{
  authenticated: boolean
  profile: { id: string; name: string } | null
}> {
  return invoke('get_auth_status')
}

export async function cancelLaunch(instanceId: string): Promise<void> {
  return invoke('cancel_launch', { instanceId })
}

export async function getGlobalSettings(): Promise<GlobalSettings> {
  return invoke('get_global_settings')
}

export async function saveGlobalSettings(settings: GlobalSettings): Promise<void> {
  return invoke('save_global_settings', { settings })
}

export async function listInstanceMods(instanceId: string): Promise<InstanceMod[]> {
  return invoke('list_instance_mods', { instanceId })
}

export async function addInstanceMod(
  instanceId: string,
  projectId: string,
  projectType: string,
  title: string,
  iconUrl: string | null,
  versionId: string | null,
  versionName: string | null,
  fileUrl: string | null,
  fileName: string | null
): Promise<InstanceMod> {
  return invoke('add_instance_mod', {
    instanceId,
    projectId,
    projectType,
    title,
    iconUrl,
    versionId,
    versionName,
    fileUrl,
    fileName
  })
}

export async function removeInstanceMod(modId: string): Promise<boolean> {
  return invoke('remove_instance_mod', { modId })
}

export async function createInstanceFromModpack(
  projectId: string,
  versionId: string | null,
  name: string
): Promise<Instance> {
  return invoke('create_instance_from_modpack', { projectId, versionId, name })
}

export async function hideTrayPanel(): Promise<void> {
  return invoke('hide_tray_panel')
}

export async function openMainWindow(): Promise<void> {
  return invoke('open_main_window')
}

export async function requestLaunchFromTray(instanceId: string): Promise<void> {
  return invoke('request_launch_from_tray', { instanceId })
}

export async function quitApp(): Promise<void> {
  return invoke('quit_app')
}

export interface AppInfo {
  name: string
  version: string
}

export async function getAppInfo(): Promise<AppInfo> {
  return invoke('get_app_info')
}