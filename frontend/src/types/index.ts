export interface Instance {
  id: string
  name: string
  description: string
  minecraft_version: string
  mod_loader: 'vanilla' | 'forge' | 'fabric' | 'quilt' | 'neoforge' | 'modrinth'
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
  release_time: string
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
  instance_id: string
  current: number
  total: number
  message: string
}

export interface LaunchLog {
  instance_id: string
  line: string
}

export interface LaunchStarted {
  instance_id: string
  pid: number
}

export interface LaunchExited {
  instance_id: string
  code: number | null
}

export interface CreateInstanceRequest {
  name: string
  description: string
  minecraft_version: string
  mod_loader: 'vanilla' | 'forge' | 'fabric' | 'quilt' | 'neoforge' | 'modrinth'
  loader_version: string | null
}

export interface GlobalSettings {
  default_java_path: string | null
  auto_install_java: boolean
  show_snapshots: boolean
  auto_update_enabled: boolean
}

export interface ModrinthSearchResult {
  project_id: string
  project_type: 'mod' | 'modpack' | 'resourcepack' | 'shader' | string
  slug: string
  author: string
  title: string
  description: string
  categories: string[]
  display_categories: string[]
  versions: string[]
  downloads: number
  follows: number
  icon_url: string | null
  date_created: string
  date_modified: string
  latest_version: string | null
  license: string
  client_side: 'required' | 'optional' | 'unsupported' | 'unknown' | string
  server_side: 'required' | 'optional' | 'unsupported' | 'unknown' | string
  gallery: string[]
  featured_gallery: string | null
  color: number | null
}

export interface ModrinthSearchResponse {
  hits: ModrinthSearchResult[]
  offset: number
  limit: number
  total_hits: number
}

export interface ModrinthProject {
  id: string
  slug: string | null
  project_type: 'mod' | 'modpack' | 'resourcepack' | 'shader' | string
  team: string
  title: string
  description: string
  body: string
  published: string
  updated: string
  approved: string | null
  status: string
  moderator_message: string | null
  license: { id: string; name: string; url: string | null } | null
  downloads: number
  followers: number
  categories: string[]
  additional_categories: string[]
  versions: string[]
  icon_url: string | null
  issues_url: string | null
  source_url: string | null
  wiki_url: string | null
  discord_url: string | null
  donation_urls: { id: string; platform: string; url: string }[]
  gallery: string[]
  featured_gallery: string | null
  color: number | null
  client_side: 'required' | 'optional' | 'unsupported' | 'unknown' | string
  server_side: 'required' | 'optional' | 'unsupported' | 'unknown' | string
}

export interface ModrinthVersion {
  id: string
  project_id: string
  author_id: string
  featured: boolean
  name: string
  version_number: string
  changelog: string | null
  changelog_url: string | null
  date_published: string
  downloads: number
  version_type: 'release' | 'beta' | 'alpha' | string
  status: string
  requested_status: string | null
  files: {
    hashes: { sha1: string; sha512: string }
    url: string
    filename: string
    primary: boolean
    size: number
    file_type: string | null
  }[]
  dependencies: {
    version_id: string | null
    project_id: string | null
    file_name: string | null
    dependency_type: string
  }[]
  game_versions: string[]
  loaders: string[]
}

export interface ModrinthCategory {
  name: string
  project_type: string
  icon: string
  header: string
}

export interface ModrinthLoader {
  name: string
  icon: string
  supported_project_types: string[]
}

export interface ModrinthGameVersion {
  version: string
  version_type: string
  date: string
  major: boolean
}

export interface InstanceMod {
  id: string
  instance_id: string
  project_id: string
  project_type: string
  title: string
  icon_url: string | null
  version_id: string | null
  version_name: string | null
  file_url: string | null
  file_name: string | null
  created_at: string
}
export * from './profile'
