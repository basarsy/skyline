import { useSyncExternalStore } from 'react'

import { isApiError, rawRequest } from '@/api/http'
import type { AuthResponse, UserResponse, UserRole, UUID } from '@/api/types'
import { decodeJwt, msUntilExpiry, roleFromClaims } from './jwt'

/**
 * The session. The access token lives in memory only; the refresh token is kept in localStorage so a reload
 * can restore the session by refreshing on boot. (An httpOnly cookie would be safer, but the API doesn't
 * support one; see README.)
 */
export const REFRESH_TOKEN_KEY = 'skyline.rt'
/** Refresh this long before the access token expires. */
const REFRESH_AHEAD_MS = 60_000

export interface SessionUser {
  id: UUID
  email: string
  role: UserRole
}

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated'

export interface AuthState {
  status: AuthStatus
  accessToken: string | null
  /** From the JWT until GET /auth/me answers, then from the server (the source of truth). */
  user: SessionUser | null
}

let state: AuthState = { status: 'loading', accessToken: null, user: null }
const listeners = new Set<() => void>()
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let refreshInFlight: Promise<boolean> | null = null
const expiredHandlers = new Set<() => void>()

function setState(next: Partial<AuthState>) {
  state = { ...state, ...next }
  listeners.forEach((listener) => listener())
}

export function getAuthState() {
  return state
}

export function getAccessToken() {
  return state.accessToken
}

export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function readRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

function writeRefreshToken(token: string | null) {
  try {
    if (token) localStorage.setItem(REFRESH_TOKEN_KEY, token)
    else localStorage.removeItem(REFRESH_TOKEN_KEY)
  } catch {
    // Storage unavailable: the session still works until the tab closes.
  }
}

function userFromToken(token: string): SessionUser | null {
  const claims = decodeJwt(token)
  if (!claims) return null
  const role = roleFromClaims(claims)
  return role ? { id: claims.userId, email: claims.sub, role } : null
}

function scheduleProactiveRefresh(token: string) {
  clearTimeout(refreshTimer)
  const claims = decodeJwt(token)
  if (!claims) return
  const delay = Math.max(msUntilExpiry(claims) - REFRESH_AHEAD_MS, 5_000)
  refreshTimer = setTimeout(() => {
    void refreshSession()
  }, delay)
}

/** Stores a fresh token pair (login or refresh). */
export function setTokens(auth: AuthResponse) {
  writeRefreshToken(auth.refreshToken)
  const fromToken = userFromToken(auth.accessToken)
  // Keep the server-confirmed user across refreshes unless the token says it's someone else.
  const user = state.user && fromToken && state.user.id === fromToken.id ? state.user : fromToken
  setState({ status: 'authenticated', accessToken: auth.accessToken, user })
  scheduleProactiveRefresh(auth.accessToken)
}

/** The user as GET /auth/me reports it. */
export function setUser(user: UserResponse) {
  setState({ user: { id: user.id, email: user.email, role: user.role } })
}

/** Client-side logout: there is no server-side logout, so the refresh token stays valid until it expires. */
export function clearSession() {
  clearTimeout(refreshTimer)
  writeRefreshToken(null)
  setState({ status: 'anonymous', accessToken: null, user: null })
}

/** Called when a session ends because it could not be refreshed (not on a deliberate logout). */
export function onSessionExpired(handler: () => void) {
  expiredHandlers.add(handler)
  return () => {
    expiredHandlers.delete(handler)
  }
}

/**
 * Rotates both tokens. Single-flight: concurrent callers (e.g. several requests that all got a 401) share
 * one refresh. Resolves true when the session is alive afterwards; on failure the session is cleared.
 */
export function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight
  const refreshToken = readRefreshToken()
  if (!refreshToken) {
    if (state.status !== 'anonymous') clearSession()
    return Promise.resolve(false)
  }

  const hadSession = state.status === 'authenticated'
  refreshInFlight = refreshWithRetry(refreshToken)
    .then((auth) => {
      setTokens(auth)
      return true
    })
    .catch((error: unknown) => {
      if (isRejected(error)) {
        // The server refused the token: the session is over.
        clearSession()
        if (hadSession) expiredHandlers.forEach((handler) => handler())
      } else {
        // Unreachable or failing server: not a verdict on the session. Keep the refresh token so a later
        // attempt (or reload) can still restore it.
        clearTimeout(refreshTimer)
        setState({ status: 'anonymous', accessToken: null, user: null })
      }
      return false
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

/** A 4xx from /auth/refresh is the server's answer; anything else (network, 5xx) is transient. */
function isRejected(error: unknown) {
  return isApiError(error) && error.status >= 400 && error.status < 500
}

const RETRY_DELAYS_MS = [400, 1200]

/** Transient failures are retried with backoff; a refusal is final immediately. */
async function refreshWithRetry(refreshToken: string): Promise<AuthResponse> {
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await rawRequest<AuthResponse>('/auth/refresh', { method: 'POST', body: { refreshToken } })
    } catch (error) {
      const delay = RETRY_DELAYS_MS[attempt]
      if (isRejected(error) || delay === undefined) throw error
      await new Promise((resolve) => setTimeout(resolve, delay))
    }
  }
}

/** For tests: back to the initial state. */
export function resetAuthStore() {
  clearTimeout(refreshTimer)
  refreshInFlight = null
  state = { status: 'loading', accessToken: null, user: null }
  listeners.forEach((listener) => listener())
}

export function markAnonymous() {
  setState({ status: 'anonymous', accessToken: null, user: null })
}

export function useAuthState(): AuthState {
  return useSyncExternalStore(subscribe, getAuthState, getAuthState)
}
