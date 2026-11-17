export type WearableKind = 'hat' | 'back' | 'pet'
type Vec3 = [number, number, number]

export interface PreviewBox {
  from: Vec3
  size: Vec3
  color?: string
  accent?: string
  pattern?: string
  uv?: [number, number]
  mirror?: boolean
}

export interface PreviewChannel {
  property: string
  wave?: string
  amplitude?: number
  speed?: number
  phase?: number
  offset?: number
  walk?: number
  walkOffset?: number
  crouch?: number
}

export interface PreviewNode {
  name: string
  pivot?: Vec3
  rotation?: Vec3
  boxes?: PreviewBox[]
  children?: PreviewNode[]
  animations?: PreviewChannel[]
}

export interface PreviewModel {
  textureWidth?: number
  textureHeight?: number
  nodes: PreviewNode[]
}

export interface BakedQuad {
  positions: number[]
  uvs: number[]
  normal: Vec3
}

export interface BakedNode {
  name: string
  pivot: Vec3
  rotation: Vec3
  quads: BakedQuad[]
  children: BakedNode[]
  animations: PreviewChannel[]
}

export interface BakedWearable {
  canvas: HTMLCanvasElement
  width: number
  height: number
  nodes: BakedNode[]
}

const MAX_TEXTURE_SIZE = 512
const PADDING = 1
const FACE_LIGHT = { top: 1.12, bottom: 0.72, east: 0.86, north: 1, west: 0.86, south: 0.94 } as const
type Face = keyof typeof FACE_LIGHT

function ceil(value: number): number {
  return Math.max(0, Math.ceil(value - 1e-4))
}

function footprintWidth(box: PreviewBox): number {
  return 2 * (ceil(box.size[0]) + ceil(box.size[2]))
}

function footprintHeight(box: PreviewBox): number {
  return ceil(box.size[2]) + ceil(box.size[1])
}

function collect(nodes: PreviewNode[], textured: boolean, painted: PreviewBox[]) {
  for (const node of nodes) {
    for (const box of node.boxes ?? []) {
      if (!textured || !box.uv) {
        painted.push(box)
      }
    }
    collect(node.children ?? [], textured, painted)
  }
}

function pack(boxes: PreviewBox[], width: number, top: number): Map<PreviewBox, [number, number]> | null {
  const order = [...boxes].sort((a, b) => footprintHeight(b) - footprintHeight(a))
  const layout = new Map<PreviewBox, [number, number]>()
  let x = 0
  let y = top
  let rowHeight = 0
  for (const box of order) {
    const boxWidth = footprintWidth(box) + PADDING
    const boxHeight = footprintHeight(box) + PADDING
    if (boxWidth > width) {
      return null
    }
    if (x + boxWidth > width) {
      x = 0
      y += rowHeight
      rowHeight = 0
    }
    if (y + boxHeight > MAX_TEXTURE_SIZE) {
      return null
    }
    layout.set(box, [x, y])
    x += boxWidth
    rowHeight = Math.max(rowHeight, boxHeight)
  }
  return layout
}

function hash(x: number, y: number): number {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263)) | 0
  h = Math.imul(h ^ (h >>> 13), 1274126177)
  return h ^ (h >>> 16)
}

function useAccent(pattern: string, face: Face, x: number, y: number, width: number, height: number, value: number): boolean {
  const vertical = face !== 'top' && face !== 'bottom'
  const broad = face === 'north' || face === 'south'
  switch (pattern) {
    case 'tipped':
      return vertical && y >= height - 1
    case 'banded':
      return vertical && Math.floor(y / 2) % 2 === 1
    case 'rim':
      return x === 0 || y === 0 || x === width - 1 || y === height - 1
    case 'spotted':
      return ((value % 7) + 7) % 7 === 0
    case 'veined':
      return broad && x % 3 === 0
    case 'quartered':
      return broad && x < Math.floor(width / 2) !== y < Math.floor(height / 2)
    case 'top':
      return face === 'top'
    default:
      return false
  }
}

function parseHex(color: string | undefined, fallback: number): number {
  return color && /^#[0-9A-Fa-f]{6}$/.test(color) ? Number.parseInt(color.slice(1), 16) : fallback
}

function paintFace(data: ImageData, box: PreviewBox, face: Face, left: number, top: number, width: number, height: number) {
  const pattern = box.pattern ?? 'solid'
  const base = parseHex(box.color, 0xffffff)
  const accent = parseHex(box.accent, base)
  for (let fy = 0; fy < height; fy++) {
    for (let fx = 0; fx < width; fx++) {
      const px = left + fx
      const py = top + fy
      if (px >= data.width || py >= data.height) {
        continue
      }
      const value = hash(px, py)
      const color = useAccent(pattern, face, fx, fy, width, height, value) ? accent : base
      let light = 1
      if (pattern !== 'glow') {
        light = FACE_LIGHT[face]
        const edge = width > 2 && height > 2 && (fx === 0 || fy === 0 || fx === width - 1 || fy === height - 1)
        if (edge && pattern !== 'rim') {
          light *= 0.88
        }
        light *= 1 + ((value & 7) - 3.5) * 0.008
      }
      const index = (py * data.width + px) * 4
      data.data[index] = Math.min(255, Math.round(((color >> 16) & 0xff) * light))
      data.data[index + 1] = Math.min(255, Math.round(((color >> 8) & 0xff) * light))
      data.data[index + 2] = Math.min(255, Math.round((color & 0xff) * light))
      data.data[index + 3] = 255
    }
  }
}

function paint(data: ImageData, u: number, v: number, box: PreviewBox) {
  const w = ceil(box.size[0])
  const h = ceil(box.size[1])
  const d = ceil(box.size[2])
  paintFace(data, box, 'top', u + d, v, w, d)
  paintFace(data, box, 'bottom', u + d + w, v, w, d)
  paintFace(data, box, 'east', u, v + d, d, h)
  paintFace(data, box, 'north', u + d, v + d, w, h)
  paintFace(data, box, 'west', u + d + w, v + d, d, h)
  paintFace(data, box, 'south', u + 2 * d + w, v + d, w, h)
}

function nextPowerOfTwo(value: number): number {
  let result = 1
  while (result < value) {
    result <<= 1
  }
  return result
}

function cubeQuads(box: PreviewBox, u: number, v: number, texWidth: number, texHeight: number): BakedQuad[] {
  const [x, y, z] = box.from
  const [width, height, depth] = box.size
  let minX = x
  let maxX = x + width
  const minY = y
  const maxY = y + height
  const minZ = z
  const maxZ = z + depth
  const mirror = box.mirror === true
  if (mirror) {
    ;[minX, maxX] = [maxX, minX]
  }
  const t0: Vec3 = [minX, minY, minZ]
  const t1: Vec3 = [maxX, minY, minZ]
  const t2: Vec3 = [maxX, maxY, minZ]
  const t3: Vec3 = [minX, maxY, minZ]
  const l0: Vec3 = [minX, minY, maxZ]
  const l1: Vec3 = [maxX, minY, maxZ]
  const l2: Vec3 = [maxX, maxY, maxZ]
  const l3: Vec3 = [minX, maxY, maxZ]
  const u0 = u
  const u1 = u + depth
  const u2 = u + depth + width
  const u22 = u + depth + width + width
  const u3 = u + depth + width + depth
  const u4 = u + depth + width + depth + width
  const v0 = v
  const v1 = v + depth
  const v2 = v + depth + height
  const faces: [Vec3[], number, number, number, number, Vec3][] = [
    [[l1, l0, t0, t1], u1, v0, u2, v1, [0, -1, 0]],
    [[t2, t3, l3, l2], u2, v1, u22, v0, [0, 1, 0]],
    [[t0, l0, l3, t3], u0, v1, u1, v2, mirror ? [1, 0, 0] : [-1, 0, 0]],
    [[t1, t0, t3, t2], u1, v1, u2, v2, [0, 0, -1]],
    [[l1, t1, t2, l2], u2, v1, u3, v2, mirror ? [-1, 0, 0] : [1, 0, 0]],
    [[l0, l1, l2, l3], u3, v1, u4, v2, [0, 0, 1]]
  ]
  return faces.map(([vertices, fu0, fv0, fu1, fv1, normal]) => {
    const coords: [number, number][] = [
      [fu1 / texWidth, fv0 / texHeight],
      [fu0 / texWidth, fv0 / texHeight],
      [fu0 / texWidth, fv1 / texHeight],
      [fu1 / texWidth, fv1 / texHeight]
    ]
    let order = [0, 1, 2, 3]
    if (mirror) {
      order = [3, 2, 1, 0]
    }
    const positions: number[] = []
    const uvs: number[] = []
    for (const index of order) {
      positions.push(...vertices[index])
      uvs.push(coords[index][0], 1 - coords[index][1])
    }
    return { positions, uvs, normal }
  })
}

function bakeNodes(nodes: PreviewNode[], layout: Map<PreviewBox, [number, number]>, width: number, height: number): BakedNode[] {
  return nodes.map((node) => ({
    name: node.name,
    pivot: node.pivot ?? [0, 0, 0],
    rotation: node.rotation ?? [0, 0, 0],
    animations: node.animations ?? [],
    quads: (node.boxes ?? []).flatMap((box) => {
      const placed = layout.get(box)
      const [u, v] = placed ?? box.uv ?? [0, 0]
      return cubeQuads(box, u, v, width, height)
    }),
    children: bakeNodes(node.children ?? [], layout, width, height)
  }))
}

export function bakeWearable(model: PreviewModel, uploaded: HTMLImageElement | null): BakedWearable {
  const textured = uploaded !== null && !!model.textureWidth && !!model.textureHeight
  const painted: PreviewBox[] = []
  collect(model.nodes, textured, painted)
  const baseWidth = textured ? uploaded!.naturalWidth : 0
  const baseHeight = textured ? uploaded!.naturalHeight : 0
  let width = Math.max(32, baseWidth)
  let layout: Map<PreviewBox, [number, number]> | null = null
  let height = baseHeight
  while (width <= MAX_TEXTURE_SIZE) {
    layout = pack(painted, width, baseHeight)
    if (layout) {
      let used = baseHeight
      for (const box of painted) {
        used = Math.max(used, layout.get(box)![1] + footprintHeight(box) + PADDING)
      }
      height = used
      if (height <= width * 2) {
        break
      }
    }
    width *= 2
  }
  if (!layout) {
    throw new Error('The model does not fit into a 512 pixel texture')
  }
  height = Math.max(16, nextPowerOfTwo(Math.max(1, height)))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')!
  if (textured) {
    context.drawImage(uploaded!, 0, 0)
  }
  const data = context.getImageData(0, 0, width, height)
  for (const box of painted) {
    const [u, v] = layout.get(box)!
    paint(data, u, v, box)
  }
  context.putImageData(data, 0, 0)
  return { canvas, width, height, nodes: bakeNodes(model.nodes, layout, width, height) }
}

const WAVES: Record<string, (x: number) => number> = {
  none: () => 0,
  sine: (x) => Math.sin(x),
  abs_sine: (x) => Math.abs(Math.sin(x)),
  half_sine: (x) => Math.max(0, Math.sin(x)),
  cubic_sine: (x) => Math.sin(x) ** 3,
  spin: (x) => x
}

export function channelValue(channel: PreviewChannel, time: number, motion: number, crouching: boolean): number {
  let value = (channel.offset ?? 0) + (channel.walkOffset ?? 0) * motion + (crouching ? (channel.crouch ?? 0) : 0)
  const wave = WAVES[channel.wave ?? 'none'] ?? WAVES.none
  const amplitude = channel.amplitude ?? 0
  if (amplitude !== 0 && channel.wave && channel.wave !== 'none') {
    value += amplitude * (1 + (channel.walk ?? 0) * motion) * wave(time * (channel.speed ?? 0) + (channel.phase ?? 0))
  }
  return value
}
