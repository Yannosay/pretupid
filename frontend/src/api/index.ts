import { invoke } from '@tauri-apps/api/core'
import type {
  CreateInstanceRequest,
  GlobalSettings,
  Instance,
  InstanceSettings,
  LaunchSettings,
  VersionEntry
} from '@/types'

export async function listInstances(): Promise<Instance[]> {
  return invoke('list_instances')
}

export async function createInstance(request: CreateInstanceRequest): Promise<Instance> {
  return invoke('create_instance', { request })
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

export async function getAuthStatus(): Promise<{ authenticated: boolean; profile: { id: string; name: string } | null }> {
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