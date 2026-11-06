import { invoke } from '@tauri-apps/api/core'
import type {
  InstanceMod,
  CreateInstanceRequest,
  GlobalSettings,
  Instance,
  InstanceSettings,
  VersionEntry,
  UpdateInstanceRequest,
  LoaderVersion,
  InstallResult,
  ModUpdate,
  SystemInfo
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

export async function updateInstance(id: string, request: UpdateInstanceRequest): Promise<Instance | null> {
  return invoke('update_instance', { id, request })
}

export async function deleteInstance(id: string): Promise<boolean> {
  return invoke('delete_instance', { id })
}

export async function duplicateInstance(id: string, name?: string): Promise<Instance> {
  return invoke('duplicate_instance', { id, name: name ?? null })
}

export async function openInstanceFolder(id: string, folder?: string): Promise<void> {
  return invoke('open_instance_folder', { id, folder: folder ?? null })
}

export async function fetchVersions(): Promise<VersionEntry[]> {
  return invoke('fetch_versions')
}

export async function getLoaderVersions(loader: string, mcVersion: string): Promise<LoaderVersion[]> {
  return invoke('get_loader_versions', { loader, mcVersion })
}

export async function launchInstance(instanceId: string, verifyFiles = false): Promise<void> {
  return invoke('launch_instance', { instanceId, verifyFiles })
}

export async function prepareInstance(instanceId: string, verifyFiles = false): Promise<void> {
  return invoke('prepare_instance', { instanceId, verifyFiles })
}

export async function listRunningInstances(): Promise<string[]> {
  return invoke('list_running_instances')
}

export async function saveInstanceSettings(id: string, settings: InstanceSettings): Promise<void> {
  return invoke('save_instance_settings', { id, settings })
}

export async function getInstanceSettings(id: string): Promise<InstanceSettings> {
  return invoke('get_instance_settings', { id })
}

export async function getSystemInfo(): Promise<SystemInfo> {
  return invoke('get_system_info')
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
  versionId: string | null = null,
  installDependencies = true
): Promise<InstallResult> {
  return invoke('add_instance_mod', { instanceId, projectId, versionId, installDependencies })
}

export async function removeInstanceMod(modId: string): Promise<boolean> {
  return invoke('remove_instance_mod', { modId })
}

export async function setInstanceModEnabled(modId: string, enabled: boolean): Promise<InstanceMod> {
  return invoke('set_instance_mod_enabled', { modId, enabled })
}

export async function checkInstanceUpdates(instanceId: string): Promise<ModUpdate[]> {
  return invoke('check_instance_updates', { instanceId })
}

export async function updateInstanceMods(instanceId: string, modIds: string[] | null = null): Promise<InstallResult> {
  return invoke('update_instance_mods', { instanceId, modIds })
}

export async function importInstanceFiles(instanceId: string, paths: string[]): Promise<InstallResult> {
  return invoke('import_instance_files', { instanceId, paths })
}

export async function createInstanceFromModpack(
  projectId: string,
  versionId: string | null,
  name: string
): Promise<Instance> {
  return invoke('create_instance_from_modpack', { projectId, versionId, name })
}

export async function importModpackFile(path: string, name: string | null = null): Promise<Instance> {
  return invoke('import_modpack_file', { path, name })
}

export async function exportInstanceModpack(instanceId: string): Promise<string> {
  return invoke('export_instance_modpack', { instanceId })
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

export interface RepairFailure {
  title: string
  file_name: string
  reason: string
}

export interface RepairReport {
  total: number
  healthy: number
  repaired: number
  failed: RepairFailure[]
}

export async function repairInstanceMods(instanceId: string): Promise<RepairReport> {
  return invoke('repair_instance_mods', { instanceId })
}
