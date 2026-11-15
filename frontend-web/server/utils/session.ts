import type { H3Event } from 'h3'

export const CHALLENGE_TTL_SECONDS = 90
export const SESSION_TTL_SECONDS = 6 * 60 * 60
export const ADMIN_SESSION_TTL_SECONDS = 8 * 60 * 60
export const ADMIN_COOKIE = '__Host-pretupid_admin'

export interface PlayerSession {
  uuid: string
  name: string
}

export async function requirePlayer(event: H3Event): Promise<PlayerSession> {
  const header = getRequestHeader(event, 'authorization') ?? ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  const session = await verifyToken(event, token, 'session')
  if (!session || !isUuid(session.sub) || !isPlayerName(session.name)) {
    throw createError({ statusCode: 401, statusMessage: 'Invalid or expired session' })
  }
  const row = await db(event).prepare('SELECT banned FROM players WHERE uuid = ?').bind(session.sub).first<{ banned: number }>()
  if (row?.banned) {
    throw createError({ statusCode: 403, statusMessage: 'This account is suspended from Pretupid services' })
  }
  return { uuid: session.sub, name: session.name }
}

function adminCookieName(event: H3Event): string {
  const host = getRequestHeader(event, 'host') ?? ''
  return host.startsWith('localhost') || host.startsWith('127.0.0.1') ? 'pretupid_admin' : ADMIN_COOKIE
}

const ADMIN_EPOCH_KEY = 'admin_session_epoch'

async function adminEpoch(event: H3Event): Promise<number> {
  const row = await db(event).prepare('SELECT value FROM app_cache WHERE key = ?').bind(ADMIN_EPOCH_KEY).first<{ value: string }>()
  const value = Number(row?.value ?? 0)
  return Number.isSafeInteger(value) ? value : 0
}

export async function revokeAdminSessions(event: H3Event): Promise<void> {
  const next = (await adminEpoch(event)) + 1
  await db(event)
    .prepare('INSERT INTO app_cache (key, value, updated_at) VALUES (?1, ?2, ?3) ON CONFLICT(key) DO UPDATE SET value = ?2, updated_at = ?3')
    .bind(ADMIN_EPOCH_KEY, String(next), nowSeconds())
    .run()
}

export async function issueAdminSession(event: H3Event): Promise<void> {
  const token = await signToken(event, {
    purpose: 'admin',
    sub: 'admin',
    epoch: await adminEpoch(event),
    exp: nowSeconds() + ADMIN_SESSION_TTL_SECONDS
  })
  const local = adminCookieName(event) !== ADMIN_COOKIE
  setCookie(event, adminCookieName(event), token, {
    httpOnly: true,
    secure: !local,
    sameSite: 'strict',
    path: '/',
    maxAge: ADMIN_SESSION_TTL_SECONDS
  })
}

export function clearAdminSession(event: H3Event): void {
  deleteCookie(event, adminCookieName(event), { path: '/', secure: adminCookieName(event) === ADMIN_COOKIE, sameSite: 'strict', httpOnly: true })
}

export async function hasAdminSession(event: H3Event): Promise<boolean> {
  const token = getCookie(event, adminCookieName(event))
  const payload = await verifyToken(event, token, 'admin')
  if (!payload) {
    return false
  }
  return (payload.epoch ?? 0) === (await adminEpoch(event))
}

export async function requireAdmin(event: H3Event): Promise<void> {
  noStore(event)
  setResponseHeader(event, 'X-Robots-Tag', 'noindex')
  if (event.method !== 'GET' && event.method !== 'HEAD') {
    requireSameOrigin(event)
    if (getRequestHeader(event, 'x-pretupid-admin') !== '1') {
      throw createError({ statusCode: 403, statusMessage: 'Missing admin request header' })
    }
  }
  if (!(await hasAdminSession(event))) {
    throw createError({ statusCode: 401, statusMessage: 'Admin session required' })
  }
}

export async function audit(event: H3Event, action: string, target: string, detail = ''): Promise<void> {
  await db(event)
    .prepare('INSERT INTO audit_log (action, target, detail, ip, created_at) VALUES (?, ?, ?, ?, ?)')
    .bind(action, target.slice(0, 128), detail.slice(0, 1000), clientIp(event), nowSeconds())
    .run()
}
