import type { H3Event } from 'h3'

export interface D1Result<T> {
  results: T[]
  success: boolean
  meta: { changes?: number; last_row_id?: number }
}

export interface D1PreparedStatement {
  bind(...values: unknown[]): D1PreparedStatement
  first<T = Record<string, unknown>>(): Promise<T | null>
  all<T = Record<string, unknown>>(): Promise<D1Result<T>>
  run(): Promise<D1Result<unknown>>
}

export interface D1Database {
  prepare(query: string): D1PreparedStatement
  batch(statements: D1PreparedStatement[]): Promise<D1Result<unknown>[]>
}

export interface R2ObjectBody {
  body: ReadableStream
  size: number
  httpEtag: string
  httpMetadata?: { contentType?: string }
}

export interface R2Bucket {
  get(key: string): Promise<R2ObjectBody | null>
  put(
    key: string,
    value: ArrayBuffer | Uint8Array | string,
    options?: { httpMetadata?: { contentType?: string; cacheControl?: string } }
  ): Promise<unknown>
  delete(key: string | string[]): Promise<void>
}

export interface PretupidBindings {
  DB: D1Database
  CONTENT: R2Bucket
}

export function bindings(event: H3Event): PretupidBindings {
  const env = (event.context as { cloudflare?: { env?: Partial<PretupidBindings> } }).cloudflare?.env
  if (!env?.DB || !env.CONTENT) {
    throw createError({ statusCode: 503, statusMessage: 'Service storage is not configured' })
  }
  return env as PretupidBindings
}

export function db(event: H3Event): D1Database {
  return bindings(event).DB
}

export function contentBucket(event: H3Event): R2Bucket {
  return bindings(event).CONTENT
}
