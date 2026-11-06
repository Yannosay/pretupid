import type { Instance } from '@/types'

export function instanceModLoaders(instance: Pick<Instance, 'mod_loader' | 'minecraft_version'>): string[] {
  switch (instance.mod_loader) {
    case 'fabric':
    case 'modrinth':
      return ['fabric']
    case 'quilt':
      return ['quilt', 'fabric']
    case 'forge':
      return ['forge']
    case 'neoforge':
      return instance.minecraft_version === '1.20.1' ? ['neoforge', 'forge'] : ['neoforge']
    default:
      return []
  }
}

export function compatibility(
  instance: Pick<Instance, 'mod_loader' | 'minecraft_version'>,
  projectType: string,
  gameVersions: string[],
  loaders: string[]
): string | null {
  if (projectType === 'modpack') return 'modpacks create their own instance'
  if (projectType === 'mod') {
    if (instance.mod_loader === 'vanilla') return 'vanilla cannot load mods'
    const supported = instanceModLoaders(instance)
    if (loaders.length > 0 && !loaders.some((l) => supported.includes(l))) return `needs ${loaders.join('/')}`
    if (gameVersions.length > 0 && !gameVersions.includes(instance.minecraft_version)) {
      return `no build for ${instance.minecraft_version}`
    }
  }
  return null
}
