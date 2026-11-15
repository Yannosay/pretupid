import type { H3Event } from 'h3'

interface PeerStub {
  fetch(input: string, init?: { method?: string; body?: string }): Promise<Response>
}

interface PeerNamespace {
  idFromName(name: string): unknown
  get(id: unknown): PeerStub
}

export function pushToPlayer(event: H3Event, uuid: string, frame: Record<string, unknown>): void {
  const env = (event.context as { cloudflare?: { env?: { PEER?: PeerNamespace } } }).cloudflare?.env
  const namespace = env?.PEER
  if (!namespace) {
    return
  }
  const stub = namespace.get(namespace.idFromName(uuid))
  const delivery = stub.fetch('https://peer/deliver', { method: 'POST', body: JSON.stringify(frame) }).catch(() => undefined)
  event.waitUntil(delivery)
}
