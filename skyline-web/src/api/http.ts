import type { ApiEnvelope } from './types'

/**
 * Base of every API call. Relative by default: same origin (Vite proxy in dev, nginx in prod), so there is
 * no CORS preflight. An absolute URL also works and relies on the gateway's CORS configuration.
 */
export const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/$/, '') || '/api/v1'

/** Any non-2xx answer, or no answer at all (status 0). `message` is user-readable. */
export class ApiError extends Error {
  readonly status: number
  /** Field path (e.g. "passenger.lastName") → message, from the envelope's `errors[]`. */
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  get hasFieldErrors() {
    return Object.keys(this.fieldErrors).length > 0
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

const FALLBACK_MESSAGES: Record<number, string> = {
  0: "We couldn't reach Skyline. Check your connection and try again.",
  400: 'Something in the request was not accepted.',
  401: 'Please log in to continue.',
  403: "You don't have access to this.",
  404: "We couldn't find what you were looking for.",
  409: 'This conflicts with something that already exists.',
  422: "This can't be done right now.",
  500: 'Something went wrong on our side. Please try again.',
  503: 'The service is busy. Please retry in a moment.',
}

export function fallbackMessage(status: number) {
  return FALLBACK_MESSAGES[status] ?? FALLBACK_MESSAGES[status >= 500 ? 500 : 400]!
}

/** "passenger.lastName: must not be blank" → { "passenger.lastName": "must not be blank" }. */
export function parseFieldErrors(errors: string[] | null | undefined): Record<string, string> {
  const result: Record<string, string> = {}
  for (const entry of errors ?? []) {
    const separator = entry.indexOf(': ')
    if (separator <= 0) continue
    const field = entry.slice(0, separator)
    // Keep the first message per field: it's the one shown under the input.
    result[field] ??= entry.slice(separator + 2)
  }
  return result
}

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return typeof value === 'object' && value !== null && 'success' in value && 'data' in value
}

/** A successful call with the server's human-readable message (shown in toasts when present). */
export interface ApiResult<T> {
  data: T
  message: string | null
}

/** Unwraps `data` on 2xx; throws a typed ApiError otherwise. */
export async function readEnvelope<T>(response: Response): Promise<T> {
  return (await readEnvelopeResult<T>(response)).data
}

/** Like readEnvelope, keeping the envelope's message. */
export async function readEnvelopeResult<T>(response: Response): Promise<ApiResult<T>> {
  const text = await response.text()
  let body: unknown = undefined
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = undefined
    }
  }

  if (response.ok) {
    return isEnvelope(body)
      ? { data: body.data as T, message: body.message?.trim() || null }
      : { data: body as T, message: null }
  }

  const envelope = isEnvelope(body) ? body : null
  throw new ApiError(
    response.status,
    envelope?.message?.trim() || fallbackMessage(response.status),
    parseFieldErrors(envelope?.errors),
  )
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | readonly (string | number)[] | null | undefined>
  signal?: AbortSignal
  headers?: Record<string, string>
}

export function buildUrl(path: string, query?: RequestOptions['query']) {
  const joined = `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
  // Resolve a relative base against the page, so it also works where fetch needs absolute URLs.
  const url = typeof window !== 'undefined' ? new URL(joined, window.location.origin).toString() : joined
  if (!query) return url
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) value.forEach((item) => params.append(key, String(item)))
    else params.append(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${url}?${qs}` : url
}

/** One HTTP call with no authentication logic. */
export async function rawRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, signal, headers = {} } = options
  let response: Response
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, fallbackMessage(0))
  }
  return readEnvelope<T>(response)
}
