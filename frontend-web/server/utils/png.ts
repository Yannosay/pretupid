const SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
const KEPT_CHUNKS = new Set(['IHDR', 'PLTE', 'tRNS', 'IDAT', 'IEND'])
const CHANNELS: Record<number, number> = { 2: 3, 3: 1, 6: 4 }

let crcTable: Uint32Array | null = null

function crc32(bytes: Uint8Array, start: number, end: number): number {
  if (!crcTable) {
    crcTable = new Uint32Array(256)
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) {
        c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      }
      crcTable[n] = c >>> 0
    }
  }
  let crc = 0xffffffff
  for (let i = start; i < end; i++) {
    crc = crcTable[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

export interface SanitizedPng {
  bytes: Uint8Array
  width: number
  height: number
}

async function inflatedLength(data: Uint8Array<ArrayBuffer>, limit: number): Promise<number> {
  const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate'))
  const reader = stream.getReader()
  let total = 0
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      return total
    }
    total += value.byteLength
    if (total > limit) {
      await reader.cancel()
      throw new Error('Image data is larger than its header declares')
    }
  }
}

export async function sanitizePng(input: Uint8Array, allowed: (width: number, height: number) => boolean): Promise<SanitizedPng> {
  if (input.byteLength < 57 || SIGNATURE.some((byte, index) => input[index] !== byte)) {
    throw new Error('File is not a PNG image')
  }
  const view = new DataView(input.buffer, input.byteOffset, input.byteLength)
  const kept: Uint8Array[] = []
  const idat: Uint8Array[] = []
  let offset = 8
  let width = 0
  let height = 0
  let colorType = -1
  let sawEnd = false
  let index = 0
  while (offset + 12 <= input.byteLength) {
    const length = view.getUint32(offset)
    const typeStart = offset + 4
    const dataStart = offset + 8
    const dataEnd = dataStart + length
    if (length > input.byteLength || dataEnd + 4 > input.byteLength) {
      throw new Error('PNG chunk is truncated')
    }
    const type = String.fromCharCode(input[typeStart], input[typeStart + 1], input[typeStart + 2], input[typeStart + 3])
    if (crc32(input, typeStart, dataEnd) !== view.getUint32(dataEnd)) {
      throw new Error('PNG checksum mismatch')
    }
    if (index === 0) {
      if (type !== 'IHDR' || length !== 13) {
        throw new Error('PNG header is missing')
      }
      width = view.getUint32(dataStart)
      height = view.getUint32(dataStart + 4)
      const bitDepth = input[dataStart + 8]
      colorType = input[dataStart + 9]
      const compression = input[dataStart + 10]
      const filter = input[dataStart + 11]
      const interlace = input[dataStart + 12]
      if (bitDepth !== 8 || !(colorType in CHANNELS) || compression !== 0 || filter !== 0 || interlace !== 0) {
        throw new Error('Use a standard 8-bit RGB or RGBA PNG without interlacing')
      }
      if (!allowed(width, height)) {
        throw new Error(`Unsupported image size ${width}x${height}`)
      }
    }
    if (KEPT_CHUNKS.has(type)) {
      kept.push(input.subarray(offset, dataEnd + 4))
      if (type === 'IDAT') {
        idat.push(input.subarray(dataStart, dataEnd))
      }
    } else if ((input[typeStart] & 0x20) === 0) {
      throw new Error(`Unsupported critical PNG chunk ${type}`)
    }
    index++
    offset = dataEnd + 4
    if (type === 'IEND') {
      sawEnd = true
      break
    }
  }
  if (!sawEnd || idat.length === 0) {
    throw new Error('PNG image data is incomplete')
  }
  const expected = height * (1 + width * CHANNELS[colorType])
  const compressed = new Uint8Array(idat.reduce((sum, part) => sum + part.byteLength, 0))
  let cursor = 0
  for (const part of idat) {
    compressed.set(part, cursor)
    cursor += part.byteLength
  }
  let actual = 0
  try {
    actual = await inflatedLength(compressed, expected)
  } catch (error) {
    throw new Error(error instanceof Error && error.message.includes('larger') ? error.message : 'PNG image data is corrupt')
  }
  if (actual !== expected) {
    throw new Error('PNG image data does not match its dimensions')
  }
  const output = new Uint8Array(8 + kept.reduce((sum, chunk) => sum + chunk.byteLength, 0))
  output.set(SIGNATURE, 0)
  let position = 8
  for (const chunk of kept) {
    output.set(chunk, position)
    position += chunk.byteLength
  }
  return { bytes: output, width, height }
}

export function isCapeSize(width: number, height: number): boolean {
  return [64, 128, 256, 512].includes(width) && height * 2 === width
}
