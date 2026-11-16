import { DurableObject } from 'cloudflare:workers'

interface Env {
  PEER: DurableObjectNamespace<Peer>
  ROOM: DurableObjectNamespace<Room>
  TUNNEL: DurableObjectNamespace<Tunnel>
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

const MAX_TUNNEL_GUESTS = 8
const MAX_TUNNEL_FRAME_BYTES = 1024 * 1024
const STREAM_PATTERN = /^[0-9a-f]{32}$/

export class Tunnel extends DurableObject<Env> {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url)
    const uuid = request.headers.get('X-Pretupid-Uuid') ?? ''
    switch (url.pathname) {
      case '/host':
        return this.host(uuid)
      case '/join':
        return this.join(uuid)
      case '/stream':
        return this.stream(url.searchParams.get('stream') ?? '')
      default:
        return new Response('Not found', { status: 404 })
    }
  }

  private tag(socket: WebSocket, prefix: string): string | null {
    return this.ctx.getTags(socket).find((tag) => tag.startsWith(prefix)) ?? null
  }

  private accept(tags: string[]): Response {
    const pair = new WebSocketPair()
    const [client, server] = Object.values(pair)
    this.ctx.acceptWebSocket(server, tags)
    return new Response(null, { status: 101, webSocket: client })
  }

  private host(uuid: string): Response {
    for (const previous of this.ctx.getWebSockets('control')) {
      previous.close(4000, 'Replaced by a new host connection')
    }
    return this.accept(['control', `uuid:${uuid}`])
  }

  private join(guest: string): Response {
    const control = this.ctx.getWebSockets('control')[0]
    if (!control) {
      return new Response('The host is not online', { status: 503 })
    }
    if (this.tag(control, 'uuid:') === `uuid:${guest}`) {
      return new Response('You cannot join your own world through the tunnel', { status: 400 })
    }
    if (this.ctx.getWebSockets('guest').length >= MAX_TUNNEL_GUESTS) {
      return new Response('This world is full', { status: 429 })
    }
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    const stream = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
    const response = this.accept(['guest', `g:${stream}`, `uuid:${guest}`])
    control.send(JSON.stringify({ t: 'open', stream, guest }))
    return response
  }

  private stream(stream: string): Response {
    if (!STREAM_PATTERN.test(stream)) {
      return new Response('Invalid stream', { status: 400 })
    }
    const guest = this.ctx.getWebSockets(`g:${stream}`)[0]
    if (!guest || this.ctx.getWebSockets(`h:${stream}`).length > 0) {
      return new Response('Stream is not waiting', { status: 404 })
    }
    const response = this.accept(['stream', `h:${stream}`])
    guest.send(JSON.stringify({ t: 'ready' }))
    return response
  }

  private partner(socket: WebSocket): WebSocket | null {
    const guestTag = this.tag(socket, 'g:')
    if (guestTag) {
      return this.ctx.getWebSockets(`h:${guestTag.slice(2)}`)[0] ?? null
    }
    const hostTag = this.tag(socket, 'h:')
    if (hostTag) {
      return this.ctx.getWebSockets(`g:${hostTag.slice(2)}`)[0] ?? null
    }
    return null
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (this.ctx.getTags(socket).includes('control')) {
      if (typeof message !== 'string' || message.length > MAX_FRAME_BYTES) {
        return
      }
      let frame: Record<string, unknown>
      try {
        frame = JSON.parse(message) as Record<string, unknown>
      } catch {
        return
      }
      if (frame.t === 'ping') {
        socket.send(JSON.stringify({ t: 'pong', at: Date.now() }))
      } else if (frame.t === 'reject' && typeof frame.stream === 'string' && STREAM_PATTERN.test(frame.stream)) {
        this.ctx.getWebSockets(`g:${frame.stream}`)[0]?.close(4003, 'The host did not let you in')
      }
      return
    }
    if (typeof message === 'string') {
      return
    }
    const partner = this.partner(socket)
    if (!partner || message.byteLength > MAX_TUNNEL_FRAME_BYTES) {
      socket.close(1008, 'Stream is not open')
      return
    }
    partner.send(message)
  }

  async webSocketClose(socket: WebSocket, code: number): Promise<void> {
    this.release(socket, code === 1000 ? 1000 : 1001)
  }

  async webSocketError(socket: WebSocket): Promise<void> {
    this.release(socket, 1011)
  }

  private release(socket: WebSocket, code: number): void {
    if (this.ctx.getTags(socket).includes('control')) {
      if (this.ctx.getWebSockets('control').some((other) => other !== socket)) {
        return
      }
      for (const other of [...this.ctx.getWebSockets('guest'), ...this.ctx.getWebSockets('stream')]) {
        other.close(1001, 'The host stopped sharing the world')
      }
      return
    }
    this.partner(socket)?.close(code, 'The other side closed the connection')
  }
}

async function authenticate(request: Request, env: Env): Promise<{ uuid: string } | Response> {
  if (request.headers.get('Upgrade') !== 'websocket') {
    return new Response('Expected WebSocket', { status: 426 })
  }
  const header = request.headers.get('Authorization') ?? ''
  const session = header.startsWith('Bearer ') ? await verifySession(env, header.slice(7)) : null
  return session ?? new Response('Invalid or expired session', { status: 401 })
}

function forward(request: Request, uuid: string, target: string): Request {
  const headers = new Headers(request.headers)
  headers.delete('Authorization')
  headers.set('X-Pretupid-Uuid', uuid)
  return new Request(target, { headers })
}

const ROUTES = new Set(['/connect', '/tunnel/host', '/tunnel/join', '/tunnel/stream'])

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)
    if (!ROUTES.has(url.pathname)) {
      return new Response('Not found', { status: 404 })
    }
    const session = await authenticate(request, env)
    if (session instanceof Response) {
      return session
    }
    const own = env.TUNNEL.get(env.TUNNEL.idFromName(session.uuid))
    switch (url.pathname) {
      case '/connect':
        return env.PEER.get(env.PEER.idFromName(session.uuid)).fetch(forward(request, session.uuid, 'https://peer/connect'))
      case '/tunnel/host':
        return own.fetch(forward(request, session.uuid, 'https://tunnel/host'))
      case '/tunnel/stream': {
        const stream = url.searchParams.get('stream') ?? ''
        return own.fetch(forward(request, session.uuid, `https://tunnel/stream?stream=${encodeURIComponent(stream)}`))
      }
      default: {
        const host = url.searchParams.get('host') ?? ''
        if (!UUID_PATTERN.test(host)) {
          return new Response('Invalid host', { status: 400 })
        }
        return env.TUNNEL.get(env.TUNNEL.idFromName(host)).fetch(forward(request, session.uuid, 'https://tunnel/join'))
      }
    }
  }
}
