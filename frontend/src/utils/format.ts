import type { Instance } from '@/types'

export function formatPlaytime(seconds: number | null | undefined): string {
  const total = Math.max(0, Math.floor(seconds ?? 0))
  if (total < 60) return total === 0 ? 'Never played' : 'Under a minute'
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hours === 0) return `${minutes} min`
  if (hours < 10 && minutes > 0) return `${hours} h ${minutes} min`
  return `${hours} h`
}

export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return 'Never'
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return 'Never'
  const diff = Date.now() - time
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return 'Just now'
  if (diff < hour) return `${Math.floor(diff / minute)} min ago`
  if (diff < day) return `${Math.floor(diff / hour)} h ago`
  if (diff < 7 * day) return `${Math.floor(diff / day)} d ago`
  return new Date(iso).toLocaleDateString()
}

const LOADER_LABELS: Record<Instance['mod_loader'], string> = {
  vanilla: 'Vanilla',
  fabric: 'Fabric',
  quilt: 'Quilt',
  forge: 'Forge',
  neoforge: 'NeoForge',
  modrinth: 'Fabric'
}

export function loaderLabel(loader: Instance['mod_loader'] | string): string {
  return LOADER_LABELS[loader as Instance['mod_loader']] ?? loader
}

export function formatCount(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}k`
  return String(value)
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[1][0]).toUpperCase()
}

export function hueFromString(value: string): number {
  let hash = 0
  for (let i = 0; i < value.length; i++) hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  return hash % 360
}
