import { getAccessToken, refreshSession } from '@/auth/auth-store'
import { ApiError, buildUrl, fallbackMessage, readEnvelope, type RequestOptions } from './http'

export { ApiError, isApiError } from './http'
export type { RequestOptions } from './http'

/** Refresh/login answer 401 for bad credentials; refreshing again would be pointless. */
const NO_REFRESH_PATHS = ['/auth/login', '/auth/refresh', '/auth/register']

async function send(path: string, options: RequestOptions, token: string | null) {
  const { method = 'GET', body, query, signal, headers = {} } = options
  try {
    return await fetch(buildUrl(path, query), {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        // Always attach the token when there is one: some public endpoints (PNR lookup) return more for
        // the owner.
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, fallbackMessage(0))
  }
}

/**
 * Calls the API, unwraps the envelope's `data`, and throws a typed ApiError otherwise. A 401 on an
 * authenticated call triggers one (shared) token refresh and a single replay; if the refresh fails the
 * session is cleared and the 401 surfaces.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = getAccessToken()
  let response = await send(path, options, token)

  if (response.status === 401 && token && !NO_REFRESH_PATHS.includes(path)) {
    const refreshed = await refreshSession()
    if (refreshed) {
      response = await send(path, options, getAccessToken())
    }
  }

  return readEnvelope<T>(response)
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) => apiFetch<T>(path, options),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiFetch<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiFetch<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiFetch<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    apiFetch<T>(path, { ...options, method: 'DELETE' }),
}
