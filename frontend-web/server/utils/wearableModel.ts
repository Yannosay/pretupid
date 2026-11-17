export const WEARABLE_KINDS = ['hat', 'back', 'pet'] as const
export type WearableKind = (typeof WEARABLE_KINDS)[number]

export const BOX_PATTERNS = ['solid', 'tipped', 'banded', 'rim', 'spotted', 'veined', 'quartered', 'glow', 'top'] as const
export const CHANNEL_PROPERTIES = ['rotX', 'rotY', 'rotZ', 'x', 'y', 'z', 'scale', 'scaleX', 'scaleY', 'scaleZ'] as const
export const CHANNEL_WAVES = ['none', 'sine', 'abs_sine', 'half_sine', 'cubic_sine', 'spin'] as const
export const WEARABLE_LIMITS = { nodes: 24, boxes: 128, depth: 4, channels: 8, coordinate: 64, size: 48, textureSize: 256 } as const

export interface WearableBox {
  from: [number, number, number]
  size: [number, number, number]
  color: string
  accent: string
  pattern: (typeof BOX_PATTERNS)[number]
  uv?: [number, number]
  mirror?: boolean
}

export interface WearableChannel {
  property: (typeof CHANNEL_PROPERTIES)[number]
  wave: (typeof CHANNEL_WAVES)[number]
  amplitude: number
  speed: number
  phase: number
  offset: number
  walk: number
  walkOffset: number
  crouch: number
}

export interface WearableNode {
  name: string
  pivot: [number, number, number]
  rotation: [number, number, number]
  boxes: WearableBox[]
  children: WearableNode[]
  animations: WearableChannel[]
}

export interface WearableModel {
  textureWidth?: number
  textureHeight?: number
  nodes: WearableNode[]
}

const NODE_NAME = /^[a-z0-9_]{1,24}$/
const HEX = /^#[0-9A-Fa-f]{6}$/

class ModelError extends Error {}

function fail(path: string, message: string): never {
  throw new ModelError(`${path}: ${message}`)
}

function record(value: unknown, path: string): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    fail(path, 'must be an object')
  }
  return value as Record<string, unknown>
}

function list(value: unknown, path: string, max: number): unknown[] {
  if (value === undefined) {
    return []
  }
  if (!Array.isArray(value)) {
    fail(path, 'must be a list')
  }
  if (value.length > max) {
    fail(path, `allows at most ${max} entries`)
  }
  return value
}

function number(value: unknown, path: string, min: number, max: number, fallback?: number): number {
  if (value === undefined && fallback !== undefined) {
    return fallback
  }
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    fail(path, 'must be a number')
  }
  if (value < min || value > max) {
    fail(path, `must be between ${min} and ${max}`)
  }
  return Math.round(value * 1000) / 1000
}

function vector(value: unknown, path: string, min: number, max: number, fallback?: [number, number, number]): [number, number, number] {
  if (value === undefined && fallback) {
    return fallback
  }
  if (!Array.isArray(value) || value.length !== 3) {
    fail(path, 'must be a list of three numbers')
  }
  return [number(value[0], `${path}[0]`, min, max), number(value[1], `${path}[1]`, min, max), number(value[2], `${path}[2]`, min, max)]
}

function pickValue<T extends readonly string[]>(values: T, value: unknown, path: string, fallback: T[number]): T[number] {
  if (value === undefined) {
    return fallback
  }
  if (typeof value !== 'string' || !(values as readonly string[]).includes(value)) {
    fail(path, `must be one of ${values.join(', ')}`)
  }
  return value as T[number]
}

function hex(value: unknown, path: string, fallback?: string): string {
  if (value === undefined && fallback) {
    return fallback
  }
  if (typeof value !== 'string' || !HEX.test(value)) {
    fail(path, 'must be a color like #A1B2C3')
  }
  return value.toUpperCase()
}

interface Budget {
  nodes: number
  boxes: number
  names: Set<string>
  textured: boolean
  textureWidth: number
  textureHeight: number
}

function parseBox(input: unknown, path: string, budget: Budget): WearableBox {
  const raw = record(input, path)
  const limit = WEARABLE_LIMITS.coordinate
  const color = hex(raw.color, `${path}.color`, '#FFFFFF')
  const box: WearableBox = {
    from: vector(raw.from, `${path}.from`, -limit, limit),
    size: vector(raw.size, `${path}.size`, 0, WEARABLE_LIMITS.size),
    color,
    accent: hex(raw.accent, `${path}.accent`, color),
    pattern: pickValue(BOX_PATTERNS, raw.pattern, `${path}.pattern`, 'solid')
  }
  if (raw.uv !== undefined) {
    if (!budget.textured) {
      fail(`${path}.uv`, 'needs an uploaded texture')
    }
    if (!Array.isArray(raw.uv) || raw.uv.length !== 2) {
      fail(`${path}.uv`, 'must be a list of two numbers')
    }
    box.uv = [
      Math.round(number(raw.uv[0], `${path}.uv[0]`, 0, budget.textureWidth - 1)),
      Math.round(number(raw.uv[1], `${path}.uv[1]`, 0, budget.textureHeight - 1))
    ]
  }
  if (raw.mirror === true) {
    box.mirror = true
  }
  return box
}

function parseChannel(input: unknown, path: string): WearableChannel {
  const raw = record(input, path)
  return {
    property: pickValue(CHANNEL_PROPERTIES, raw.property, `${path}.property`, 'rotY'),
    wave: pickValue(CHANNEL_WAVES, raw.wave, `${path}.wave`, 'none'),
    amplitude: number(raw.amplitude, `${path}.amplitude`, -720, 720, 0),
    speed: number(raw.speed, `${path}.speed`, 0, 10, 0),
    phase: number(raw.phase, `${path}.phase`, -100, 100, 0),
    offset: number(raw.offset, `${path}.offset`, -720, 720, 0),
    walk: number(raw.walk, `${path}.walk`, -10, 10, 0),
    walkOffset: number(raw.walkOffset, `${path}.walkOffset`, -720, 720, 0),
    crouch: number(raw.crouch, `${path}.crouch`, -720, 720, 0)
  }
}

function parseNode(input: unknown, path: string, depth: number, budget: Budget): WearableNode {
  if (depth > WEARABLE_LIMITS.depth) {
    fail(path, `nodes can be nested at most ${WEARABLE_LIMITS.depth} levels deep`)
  }
  budget.nodes++
  if (budget.nodes > WEARABLE_LIMITS.nodes) {
    fail(path, `a wearable can have at most ${WEARABLE_LIMITS.nodes} nodes`)
  }
  const raw = record(input, path)
  if (typeof raw.name !== 'string' || !NODE_NAME.test(raw.name)) {
    fail(`${path}.name`, 'must be 1-24 lowercase letters, digits or _')
  }
  if (budget.names.has(raw.name)) {
    fail(`${path}.name`, `"${raw.name}" is used twice`)
  }
  budget.names.add(raw.name)
  const boxes = list(raw.boxes, `${path}.boxes`, WEARABLE_LIMITS.boxes).map((box, index) => parseBox(box, `${path}.boxes[${index}]`, budget))
  budget.boxes += boxes.length
  if (budget.boxes > WEARABLE_LIMITS.boxes) {
    fail(path, `a wearable can have at most ${WEARABLE_LIMITS.boxes} boxes`)
  }
  const animations = list(raw.animations, `${path}.animations`, WEARABLE_LIMITS.channels).map((channel, index) =>
    parseChannel(channel, `${path}.animations[${index}]`)
  )
  const children = list(raw.children, `${path}.children`, WEARABLE_LIMITS.nodes).map((child, index) =>
    parseNode(child, `${path}.children[${index}]`, depth + 1, budget)
  )
  return {
    name: raw.name,
    pivot: vector(raw.pivot, `${path}.pivot`, -WEARABLE_LIMITS.coordinate, WEARABLE_LIMITS.coordinate, [0, 0, 0]),
    rotation: vector(raw.rotation, `${path}.rotation`, -360, 360, [0, 0, 0]),
    boxes,
    children,
    animations
  }
}

export function parseWearableModel(input: unknown, texture: { width: number; height: number } | null): WearableModel {
  try {
    const raw = record(input, 'model')
    const budget: Budget = {
      nodes: 0,
      boxes: 0,
      names: new Set(),
      textured: texture !== null,
      textureWidth: texture?.width ?? 0,
      textureHeight: texture?.height ?? 0
    }
    const nodes = list(raw.nodes, 'model.nodes', WEARABLE_LIMITS.nodes).map((node, index) => parseNode(node, `model.nodes[${index}]`, 1, budget))
    if (budget.boxes === 0) {
      fail('model', 'needs at least one box')
    }
    const model: WearableModel = { nodes }
    if (texture) {
      model.textureWidth = texture.width
      model.textureHeight = texture.height
    }
    return model
  } catch (error) {
    if (error instanceof ModelError) {
      throw createError({ statusCode: 400, statusMessage: error.message.slice(0, 200) })
    }
    throw error
  }
}

export function isWearableKind(value: unknown): value is WearableKind {
  return typeof value === 'string' && (WEARABLE_KINDS as readonly string[]).includes(value)
}

export function isWearableTextureSize(width: number, height: number): boolean {
  const sizes = [16, 32, 64, 128, 256]
  return sizes.includes(width) && sizes.includes(height)
}
