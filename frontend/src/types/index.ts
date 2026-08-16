export interface Instance {
  id: string
  name: string
  description: string
  minecraft_version: string
  mod_loader: 'vanilla' | 'forge' | 'fabric' | 'quilt'
  loader_version: string | null
  created_at: string
  updated_at: string
}

export interface InstanceSettings {
  memory_min_mb: number
  memory_max_mb: number
  resolution_width: number
  resolution_height: number
  fullscreen: boolean
  java_path: string | null
  extra_jvm_args: string[]
  extra_game_args: string[]
}

export interface VersionEntry {
  id: string
  type: string
  url: string
  time: string
  releaseTime: string
  sha1: string
}

export interface LaunchSettings {
  memory_min_mb: number
  memory_max_mb: number
  resolution_width: number
  resolution_height: number
  fullscreen: boolean
  java_path: string | null
  auto_install_java: boolean
  extra_jvm_args: string[]
  extra_game_args: string[]
}

export interface LaunchProgress {
  current: number
  total: number
  message: string
}

export interface LaunchLog {
  line: string
}

export interface LaunchStarted {
  pid: number
}

export interface LaunchExited {
  code: number | null
}

export interface CreateInstanceRequest {
  name: string
  description: string
  minecraft_version: string
  mod_loader: 'vanilla' | 'forge' | 'fabric' | 'quilt'
  loader_version: string | null
}
export interface GlobalSettings {
  default_java_path: string | null
  auto_install_java: boolean
  show_snapshots: boolean
}