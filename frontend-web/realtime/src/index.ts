import { DurableObject } from 'cloudflare:workers'

interface Env {
  PEER: DurableObjectNamespace<Peer>
  ROOM: DurableObjectNamespace<Room>
  PRESENCE_SECRET: string
}

interface Attachment {
  uuid: string
  room: string | null
  windowStart: number
  count: number
}

const MAX_FRAME_BYTES = 2048
const MAX_FRAMES_PER_10S = 40
const MAX_ROOM_MEMBERS = 1000
const ROOM_PATTERN = /^[0-9a-f]{64}$/
const ID_PATTERN = /^[a-z0-9][a-z0-9_-]{0,31}$/
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const encoder = new TextEncoder()
let cachedKey: { secret: string; key: CryptoKey } | null = null

function fromBase64url(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(normalized + '='.repeat((4 - (normalized.length % 4)) % 4))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

async function verifySession(env: Env, token: string): Promise<{ uuid: string } | null> {
  if (typeof env.PRESENCE_SECRET !== 'string' || env.PRESENCE_SECRET.length < 32 || token.length > 1024) {
    return null
  }
  const parts = token.split('.')
  if (parts.length !== 2) {
    return null
  }
  const secret = `session:${env.PRESENCE_SECRET}`
  if (cachedKey?.secret !== secret) {
    const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify'])
    cachedKey = { secret, key }
  }
  try {
    const valid = await crypto.subtle.verify('HMAC', cachedKey.key, fromBase64url(parts[1]), encoder.encode(parts[0]))
    if (!valid) {
      return null
    }
    const payload = JSON.parse(new TextDecoder().decode(fromBase64url(parts[0]))) as { purpose?: string; sub?: string; exp?: number }
    if (payload.purpose !== 'session' || typeof payload.exp !== 'number' || payload.exp * 1000 < Date.now()) {
      return null
    }
    return typeof payload.sub === 'string' && UUID_PATTERN.test(payload.sub) ? { uuid: payload.sub } : null
  } catch {
    return null
  }
}

export class Peer extends DurableObject<Env> {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url)
    if (url.pathname === '/deliver') {
      const sockets = this.ctx.getWebSockets()
      if (sockets.length === 0) {
        return new Response(null, { status: 410 })
      }
      const body = await request.text()
      for (const socket of sockets) {
        try {
          socket.send(body)
        } catch {
          socket.close(1011, 'delivery failed')
        }
      }
      return new Response(null, { status: 204 })
    }
    const uuid = request.headers.get('X-Pretupid-Uuid')
    if (request.headers.get('Upgrade') !== 'websocket' || !uuid) {
      return new Response('Expected WebSocket', { status: 426 })
    }
    const existing = this.ctx.getWebSockets()
    if (existing.length >= 3) {
      existing[0].close(4000, 'Too many connections')
    }
    const pair = new WebSocketPair()
    const [client, server] = Object.values(pair)
    this.ctx.acceptWebSocket(server)
    server.serializeAttachment({ uuid, room: null, windowStart: Date.now(), count: 0 } satisfies Attachment)
    server.send(JSON.stringify({ t: 'hello', at: Date.now() }))
    return new Response(null, { status: 101, webSocket: client })
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    const attachment = socket.deserializeAttachment() as Attachment
    const now = Date.now()
    if (now - attachment.windowStart > 10_000) {
      attachment.windowStart = now
      attachment.count = 0
    }
    attachment.count++
    if (attachment.count > MAX_FRAMES_PER_10S) {
      socket.close(4008, 'Rate limited')
      return
    }
    if (typeof message !== 'string' || message.length > MAX_FRAME_BYTES) {
      socket.close(1009, 'Frame too large')
      return
    }
    let frame: Record<string, unknown>
    try {
      frame = JSON.parse(message) as Record<string, unknown>
    } catch {
      return
    }
    switch (frame.t) {
      case 'ping':
        socket.send(JSON.stringify({ t: 'pong', at: now }))
        break
      case 'join': {
        const room = typeof frame.room === 'string' && ROOM_PATTERN.test(frame.room) ? frame.room : null
        if (attachment.room !== room) {
          if (attachment.room) {
            await this.room(attachment.room, '/leave', { uuid: attachment.uuid })
          }
          if (room) {
            await this.room(room, '/join', { uuid: attachment.uuid })
          }
          attachment.room = room
        }
        break
      }
      case 'emote': {
        const id = frame.id === null ? null : typeof frame.id === 'string' && ID_PATTERN.test(frame.id) ? frame.id : undefined
        if (id !== undefined && attachment.room) {
          await this.room(attachment.room, '/broadcast', { from: attachment.uuid, frame: { t: 'emote', u: attachment.uuid, id, at: now } })
        }
        break
      }
      case 'profile':
        if (attachment.room) {
          await this.room(attachment.room, '/broadcast', { from: attachment.uuid, frame: { t: 'profile', u: attachment.uuid } })
        }
        break
      default:
        break
    }
    socket.serializeAttachment(attachment)
  }

  async webSocketClose(socket: WebSocket): Promise<void> {
    await this.leave(socket)
  }

  async webSocketError(socket: WebSocket): Promise<void> {
    await this.leave(socket)
  }

  private async leave(socket: WebSocket): Promise<void> {
    const attachment = socket.deserializeAttachment() as Attachment | null
    if (attachment?.room) {
      const stillPresent = this.ctx.getWebSockets().some((other) => other !== socket && (other.deserializeAttachment() as Attachment | null)?.room === attachment.room)
      if (!stillPresent) {
        await this.room(attachment.room, '/leave', { uuid: attachment.uuid })
      }
    }
  }

  private async room(room: string, path: string, body: unknown): Promise<void> {
    const stub = this.env.ROOM.get(this.env.ROOM.idFromName(room))
    await stub.fetch(`https://room${path}`, { method: 'POST', body: JSON.stringify(body) }).catch(() => undefined)
  }
}

export class Room extends DurableObject<Env> {
  private members: Set<string> | null = null

  private async load(): Promise<Set<string>> {
    if (!this.members) {
      this.members = new Set((await this.ctx.storage.get<string[]>('members')) ?? [])
    }
    return this.members
  }

  private async save(): Promise<void> {
    await this.ctx.storage.put('members', [...(this.members ?? [])])
  }

  async fetch(request: Request): Promise<Response> {
    const path = new URL(request.url).pathname
    const body = (await request.json()) as { uuid?: string; from?: string; frame?: unknown }
    const members = await this.load()
    if (path === '/join' && body.uuid && UUID_PATTERN.test(body.uuid)) {
      if (members.size < MAX_ROOM_MEMBERS) {
        members.add(body.uuid)
        await this.save()
      }
    } else if (path === '/leave' && body.uuid) {
      if (members.delete(body.uuid)) {
        await this.save()
      }
    } else if (path === '/broadcast' && body.from && members.has(body.from)) {
      const payload = JSON.stringify(body.frame)
      const gone: string[] = []
      await Promise.all(
        [...members]
          .filter((member) => member !== body.from)
          .map(async (member) => {
            const stub = this.env.PEER.get(this.env.PEER.idFromName(member))
            const response = await stub.fetch('https://peer/deliver', { method: 'POST', body: payload }).catch(() => null)
            if (response?.status === 410) {
              gone.push(member)
            }
          })
      )
      if (gone.length > 0) {
        gone.forEach((member) => members.delete(member))
        await this.save()
      }
    }
    return new Response(null, { status: 204 })
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)
    if (url.pathname !== '/connect') {
      return new Response('Not found', { status: 404 })
    }
    if (request.headers.get('Upgrade') !== 'websocket') {
      return new Response('Expected WebSocket', { status: 426 })
    }
    const header = request.headers.get('Authorization') ?? ''
    const session = header.startsWith('Bearer ') ? await verifySession(env, header.slice(7)) : null
    if (!session) {
      return new Response('Invalid or expired session', { status: 401 })
    }
    const headers = new Headers(request.headers)
    headers.delete('Authorization')
    headers.set('X-Pretupid-Uuid', session.uuid)
    const stub = env.PEER.get(env.PEER.idFromName(session.uuid))
    return stub.fetch(new Request('https://peer/connect', { headers }))
  }
}
