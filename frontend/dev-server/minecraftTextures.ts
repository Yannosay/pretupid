import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { inflateRawSync } from 'node:zlib'
import type { Plugin } from 'vite'

const TEXTURE_PREFIX = 'assets/minecraft/textures/block/'
const TEXTURE_NAME = /^[a-z0-9_]{1,48}$/
const ROUTE = '/__dev/block-textures/'

interface ZipEntry {
  method: number
  compressedSize: number
  localOffset: number
}

function readCentralDirectory(zip: Buffer): Map<string, ZipEntry> {
  const entries = new Map<string, ZipEntry>()
  const searchStart = Math.max(0, zip.length - 65_557)
  let end = -1
  for (let i = zip.length - 22; i >= searchStart; i--) {
    if (zip.readUInt32LE(i) === 0x06054b50) {
      end = i
      break
    }
  }
  if (end < 0) return entries
  let offset = zip.readUInt32LE(end + 16)
  const count = zip.readUInt16LE(end + 10)
  for (let i = 0; i < count && zip.readUInt32LE(offset) === 0x02014b50; i++) {
    const nameLength = zip.readUInt16LE(offset + 28)
    const extraLength = zip.readUInt16LE(offset + 30)
    const commentLength = zip.readUInt16LE(offset + 32)
    const name = zip.toString('utf8', offset + 46, offset + 46 + nameLength)
    entries.set(name, {
      method: zip.readUInt16LE(offset + 10),
      compressedSize: zip.readUInt32LE(offset + 20),
      localOffset: zip.readUInt32LE(offset + 42)
    })
    offset += 46 + nameLength + extraLength + commentLength
  }
  return entries
}

function readEntry(zip: Buffer, entry: ZipEntry): Buffer {
  const header = entry.localOffset
  const dataStart = header + 30 + zip.readUInt16LE(header + 26) + zip.readUInt16LE(header + 28)
  const data = zip.subarray(dataStart, dataStart + entry.compressedSize)
  return entry.method === 8 ? inflateRawSync(data) : Buffer.from(data)
}

function candidateJars(): string[] {
  const override = process.env.PRETUPID_DEV_MINECRAFT_JAR
  if (override) return [override]
  const appData = process.env.APPDATA ?? path.join(os.homedir(), '.local', 'share')
  const versions = path.join(appData, 'PretupidLauncher', 'caches', 'versions')
  if (!existsSync(versions)) return []
  return readdirSync(versions)
    .map((id) => path.join(versions, id, `${id}.jar`))
    .filter((jar) => existsSync(jar))
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)
}

function loadTextures(): Map<string, Buffer> | null {
  for (const jar of candidateJars()) {
    const zip = readFileSync(jar)
    const entries = readCentralDirectory(zip)
    if (!entries.has(`${TEXTURE_PREFIX}stone.png`)) continue
    const textures = new Map<string, Buffer>()
    for (const [name, entry] of entries) {
      if (!name.startsWith(TEXTURE_PREFIX) || !name.endsWith('.png')) continue
      const texture = name.slice(TEXTURE_PREFIX.length, -4)
      if (TEXTURE_NAME.test(texture)) textures.set(texture, readEntry(zip, entry))
    }
    return textures
  }
  return null
}

export function localMinecraftTextures(): Plugin {
  let textures: Map<string, Buffer> | null | undefined
  return {
    name: 'pretupid-dev-minecraft-textures',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use(ROUTE, (request, response) => {
        textures ??= loadTextures()
        const file = (request.url ?? '').replace(/^\//, '').split('?')[0]
        if (file === 'index.json') {
          response.setHeader('Content-Type', 'application/json')
          response.end(JSON.stringify(textures ? [...textures.keys()].sort() : []))
          return
        }
        const texture = textures?.get(file.replace(/\.png$/, ''))
        if (!texture) {
          response.statusCode = 404
          response.end()
          return
        }
        response.setHeader('Content-Type', 'image/png')
        response.setHeader('Cache-Control', 'max-age=3600')
        response.end(texture)
      })
    }
  }
}
