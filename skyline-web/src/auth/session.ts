import { authApi } from '@/api/endpoints/auth'
import { isApiError } from '@/api/http'
import type { LoginRequest, RegisterRequest, UserResponse } from '@/api/types'
import { queryClient } from '@/app/query-client'
import {
  clearSession,
  getAuthState,
  markAnonymous,
  readRefreshToken,
  refreshSession,
  setTokens,
  setUser,
} from './auth-store'

/** Loads GET /auth/me, the source of truth for email and role. */
export async function loadCurrentUser(): Promise<UserResponse | null> {
  try {
    const me = await authApi.me()
    setUser(me)
    return me
  } catch (error) {
    // 401 here means the session is gone: the client already tried a refresh.
    if (isApiError(error) && error.status === 401) clearSession()
    return null
  }
}

let bootPromise: Promise<void> | null = null

/** On app start: restore the session from the stored refresh token, if any. Runs once. */
export function bootstrapSession(): Promise<void> {
  bootPromise ??= (async () => {
    if (!readRefreshToken()) {
      markAnonymous()
      return
    }
    if (await refreshSession()) await loadCurrentUser()
  })()
  return bootPromise
}

export async function login(credentials: LoginRequest): Promise<UserResponse | null> {
  const auth = await authApi.login({ email: credentials.email.trim(), password: credentials.password })
  // A different account may have been logged in in this tab: drop its cached data.
  queryClient.clear()
  setTokens(auth)
  return loadCurrentUser()
}

/** Registration doesn't log in; log in right away with the same credentials. */
export async function register(details: RegisterRequest): Promise<UserResponse | null> {
  await authApi.register({ email: details.email.trim(), password: details.password })
  return login(details)
}

/** Client-side only: the API has no logout endpoint. */
export function logout() {
  clearSession()
  queryClient.clear()
}

export function isLoggedIn() {
  return getAuthState().status === 'authenticated'
}

/** Where a user lands after logging in. */
export function homeFor(role: UserResponse['role'] | undefined) {
  return role === 'STAFF' || role === 'ADMIN' ? '/ops/flights' : '/trips'
}

/** Only same-app paths are honoured as a post-login destination (no open redirects). */
export function safeNext(next: string | null | undefined): string | null {
  if (!next || !next.startsWith('/') || next.startsWith('//')) return null
  if (next.startsWith('/login') || next.startsWith('/register')) return null
  return next
}
