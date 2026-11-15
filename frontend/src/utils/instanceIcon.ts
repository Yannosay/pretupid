import { reactive } from 'vue'
import { invoke } from '@tauri-apps/api/core'
import { hueFromString } from '@/utils/format'

export interface BlockMaterial {
  label: string
  texture: string
}

export const BLOCK_MATERIALS: Record<string, BlockMaterial> = {
  grass: { label: 'Grass block', texture: 'grass_block_side' },
  stone: { label: 'Stone', texture: 'stone' },
  cobblestone: { label: 'Cobblestone', texture: 'cobblestone' },
  mossy: { label: 'Mossy cobblestone', texture: 'mossy_cobblestone' },
  deepslate: { label: 'Deepslate', texture: 'deepslate' },
  planks: { label: 'Oak planks', texture: 'oak_planks' },
  cherry: { label: 'Cherry planks', texture: 'cherry_planks' },
  log: { label: 'Oak log', texture: 'oak_log' },
  crafting: { label: 'Crafting table', texture: 'crafting_table_front' },
  bookshelf: { label: 'Bookshelf', texture: 'bookshelf' },
  sand: { label: 'Sand', texture: 'sand' },
  snow: { label: 'Snow', texture: 'snow' },
  ice: { label: 'Packed ice', texture: 'packed_ice' },
  bricks: { label: 'Bricks', texture: 'bricks' },
  tnt: { label: 'TNT', texture: 'tnt_side' },
  pumpkin: { label: 'Pumpkin', texture: 'pumpkin_side' },
  diamond: { label: 'Diamond ore', texture: 'diamond_ore' },
  emerald: { label: 'Emerald ore', texture: 'emerald_ore' },
  gold: { label: 'Gold ore', texture: 'gold_ore' },
  redstone: { label: 'Redstone ore', texture: 'redstone_ore' },
  lapis: { label: 'Lapis ore', texture: 'lapis_ore' },
  copper: { label: 'Copper block', texture: 'copper_block' },
  amethyst: { label: 'Amethyst', texture: 'amethyst_block' },
  glowstone: { label: 'Glowstone', texture: 'glowstone' },
  obsidian: { label: 'Obsidian', texture: 'obsidian' },
  netherrack: { label: 'Netherrack', texture: 'netherrack' },
  endstone: { label: 'End stone', texture: 'end_stone' },
  prismarine: { label: 'Prismarine', texture: 'prismarine' }
}

const AUTO_MATERIALS = ['grass', 'stone', 'planks', 'log', 'cherry', 'crafting', 'bookshelf', 'sand', 'bricks', 'diamond', 'emerald', 'gold', 'redstone', 'lapis', 'amethyst', 'prismarine', 'endstone', 'copper']

export function parseIconStyle(value: string | null | undefined): string | null {
  if (!value) return null
  const [kind, name] = value.split(':')
  return kind === 'block' && name in BLOCK_MATERIALS ? name : null
}

export function formatIconStyle(material: string): string {
  return `block:${material}`
}

export function autoMaterial(seed: string): string {
  return AUTO_MATERIALS[hueFromString(seed) % AUTO_MATERIALS.length]
}

const textures = reactive(new Map<string, string>())
let request: Promise<void> | null = null

export function loadBlockTextures(force = false): Promise<void> {
  if (request && !force) return request
  request = (async () => {
    try {
      const names = Object.values(BLOCK_MATERIALS).map((m) => m.texture)
      const found = await invoke<Record<string, string>>('get_block_textures', { names })
      for (const [name, url] of Object.entries(found)) textures.set(name, url)
    } catch {
      request = null
    }
  })()
  return request
}

export function hasBlockTextures(): boolean {
  return textures.size > 0
}

export function blockTexture(material: string): string | null {
  const entry = BLOCK_MATERIALS[material] ?? BLOCK_MATERIALS.stone
  return textures.get(entry.texture) ?? null
}

const MAX_ICON_SOURCE_BYTES = 10 * 1024 * 1024

export async function imageFileToIcon(file: File, size = 128): Promise<string> {
  if (!file.type.startsWith('image/')) throw new Error('Pick a PNG, JPEG, WebP or GIF image.')
  if (file.size > MAX_ICON_SOURCE_BYTES) throw new Error('That image is larger than 10 MB.')
  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.decoding = 'async'
    image.src = url
    await image.decode()
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Could not process the image.')
    const side = Math.min(image.naturalWidth, image.naturalHeight)
    const sx = (image.naturalWidth - side) / 2
    const sy = (image.naturalHeight - side) / 2
    const pixelArt = side <= 64
    ctx.imageSmoothingEnabled = !pixelArt
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(image, sx, sy, side, side, 0, 0, size, size)
    return canvas.toDataURL('image/png')
  } finally {
    URL.revokeObjectURL(url)
  }
}
