import { useSyncExternalStore } from 'react'

import { ApiError, isApiError, rawRequest } from '@/api/http'
import type { AuthResponse, UserResponse, UserRole, UUID } from '@/api/types'
import { clearLastPassengers } from '@/lib/last-passenger'
import { decodeJwt, msUntilExpiry, roleFromClaims } from './jwt'

/**
 * The session. The access token lives in memory only; the refresh token is kept in localStorage so a reload
 * can restore the session by refreshing on boot. (An httpOnly cookie would be safer, but the API doesn't
 * support one; see README.) The server rotates the refresh token on every refresh and revokes the old one,
 * so tabs share it through storage and take turns refreshing (see refreshSession).
 */
export const REFRESH_TOKEN_KEY = 'skyline.rt'
/** Refresh this long before the access token expires. */
const REFRESH_AHEAD_MS = 60_000
/** Shared by every tab of the app: only one refreshes at a time. */
const REFRESH_LOCK = 'skyline-refresh'

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
/** Consecutive refreshes that failed transiently, for the background retry's backoff. */
let transientFailures = 0
/** Bumped whenever the session ends, so a refresh that was in flight can't bring it back. */
let generation = 0
const expiredHandlers = new Set<() => void>()
const signedOutElsewhereHandlers = new Set<() => void>()

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

function scheduleRefresh(delay: number) {
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => {
    void refreshSession()
  }, delay)
}

function scheduleProactiveRefresh(token: string) {
  const claims = decodeJwt(token)
  if (!claims) return clearTimeout(refreshTimer)
  scheduleRefresh(Math.max(msUntilExpiry(claims) - REFRESH_AHEAD_MS, 5_000))
}

/** Stores a fresh token pair (login or refresh). */
export function setTokens(auth: AuthResponse) {
  transientFailures = 0
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

/**
 * Ends the session in this browser: forgets the tokens and the passenger prefill. Revoking the refresh token
 * on the server is up to the caller (see logout in ./session).
 */
export function clearSession() {
  generation += 1
  clearTimeout(refreshTimer)
  transientFailures = 0
  writeRefreshToken(null)
  clearLastPassengers()
  setState({ status: 'anonymous', accessToken: null, user: null })
}

/**
 * POST /auth/logout, in the background. Best-effort: it answers 204 whatever the token, and a failure only
 * means the token stays valid until it expires.
 */
export function revokeRefreshToken(refreshToken: string) {
  rawRequest<void>('/auth/logout', { method: 'POST', body: { refreshToken } }).catch(() => {})
}

/** Called when a session ends because it could not be refreshed (not on a deliberate logout). */
export function onSessionExpired(handler: () => void) {
  expiredHandlers.add(handler)
  return () => {
    expiredHandlers.delete(handler)
  }
}

/** Called when another tab logged out, after this tab's session was cleared too. */
export function onSignedOutElsewhere(handler: () => void) {
  signedOutElsewhereHandlers.add(handler)
  return () => {
    signedOutElsewhereHandlers.delete(handler)
  }
}

/**
 * Rotates both tokens. Single-flight within the tab (concurrent 401s share one refresh) and serialized across
 * tabs with a Web Lock, so two tabs never spend the same refresh token. Resolves true when the session is
 * alive afterwards. A 4xx ends the session; a network error or 5xx keeps it and retries in the background.
 */
export function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight
  if (!readRefreshToken()) {
    if (state.status !== 'anonymous') clearSession()
    return Promise.resolve(false)
  }

  const hadSession = state.status === 'authenticated'
  const started = generation
  refreshInFlight = withRefreshLock(rotateStoredToken)
    .then((auth) => {
      // Logged out meanwhile: don't bring the session back, and don't leave the new token valid either.
      if (generation !== started) {
        revokeRefreshToken(auth.refreshToken)
        return false
      }
      setTokens(auth)
      return true
    })
    .catch((error: unknown) => {
      if (generation !== started) return false
      if (isRejected(error)) {
        // The server refused the token: the session is over.
        clearSession()
        if (hadSession) expiredHandlers.forEach((handler) => handler())
      } else {
        // Unreachable or failing server: not a verdict on the session. Keep it (and the refresh token) and
        // try again later. Requests may fail meanwhile, but the user stays logged in.
        if (state.status === 'loading') setState({ status: 'anonymous' })
        transientFailures += 1
        scheduleRefresh(Math.min(5_000 * 2 ** (transientFailures - 1), 60_000))
      }
      return false
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  return locks ? locks.request(REFRESH_LOCK, task) : task()
}

/**
 * Runs while holding the lock. The token is read from storage here, not before: another tab may have rotated
 * it while this one waited, and the old one is revoked by now. Without Web Locks a refusal can still race with
 * another tab's rotation; then try again with the newer token.
 */
async function rotateStoredToken(): Promise<AuthResponse> {
  for (;;) {
    const refreshToken = readRefreshToken()
    if (!refreshToken) throw new ApiError(401, 'Logged out')
    try {
      return await refreshWithRetry(refreshToken)
    } catch (error) {
      const latest = readRefreshToken()
      if (!isRejected(error) || !latest || latest === refreshToken) throw error
    }
  }
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

/**
 * Other tabs. Logging out anywhere logs out everywhere. A token another tab rotated needs nothing here (the
 * next refresh reads it from storage), but an anonymous tab adopts a session another tab started.
 */
function onStorage(event: StorageEvent) {
  if (event.key !== REFRESH_TOKEN_KEY && event.key !== null) return
  if (!readRefreshToken()) {
    if (state.status === 'anonymous') return
    clearSession()
    signedOutElsewhereHandlers.forEach((handler) => handler())
  } else if (state.status === 'anonymous') {
    void refreshSession()
  }
}

if (typeof window !== 'undefined') window.addEventListener('storage', onStorage)

/** For tests: back to the initial state. */
export function resetAuthStore() {
  clearTimeout(refreshTimer)
  refreshInFlight = null
  transientFailures = 0
  generation += 1
  state = { status: 'loading', accessToken: null, user: null }
  listeners.forEach((listener) => listener())
}

export function markAnonymous() {
  setState({ status: 'anonymous', accessToken: null, user: null })
}

export function useAuthState(): AuthState {
  return useSyncExternalStore(subscribe, getAuthState, getAuthState)
}
