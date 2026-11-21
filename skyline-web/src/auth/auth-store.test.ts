import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { rawRequest } from '@/api/http'
import { readLastPassenger, saveLastPassenger } from '@/lib/last-passenger'
import { fakeAuth as tokens, TEST_USER as user } from '@/test/fixtures'
import { fail, issueSessionFor, ok } from '@/test/handlers'
import { server } from '@/test/server'
import {
  getAuthState,
  markAnonymous,
  onSessionEnded,
  onSessionExpired,
  REFRESH_TOKEN_KEY,
  refreshSession,
  setTokens,
} from './auth-store'
import { logout } from './session'

/** Records the refresh tokens the server was sent, answering with a rotated pair. */
function rotatingRefresh() {
  const seen: string[] = []
  server.use(
    http.post('*/api/v1/auth/refresh', async ({ request }) => {
      const { refreshToken } = (await request.json()) as { refreshToken: string }
      seen.push(refreshToken)
      return ok(tokens(`${refreshToken}+1`))
    }),
  )
  return seen
}

const until = (check: () => boolean) => vi.waitFor(() => expect(check()).toBe(true))

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('refresh failures', () => {
  it('keeps the session through a failing server', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => new HttpResponse('Unavailable', { status: 503 })))
    setTokens(tokens('rt-1'))
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)

    await expect(refreshSession()).resolves.toBe(false)
    expect(getAuthState().status).toBe('authenticated')
    expect(getAuthState().accessToken).not.toBeNull()
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('rt-1')
    expect(expired).not.toHaveBeenCalled()
    unsubscribe()
  }, 10_000)

  it('ends the session when the server refuses the token', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => fail(401, 'Invalid refresh token')))
    setTokens(tokens('rt-dead'))
    saveLastPassenger(user.userId, { firstName: 'Ada' })
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)

    await expect(refreshSession()).resolves.toBe(false)
    expect(getAuthState().status).toBe('anonymous')
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    expect(readLastPassenger(user.userId)).toEqual({})
    expect(expired).toHaveBeenCalledOnce()
    unsubscribe()
  })
})

describe('across tabs', () => {
  it('refreshes with the token another tab rotated in, not the one this tab last had', async () => {
    const seen = rotatingRefresh()
    setTokens(tokens('rt-1'))
    // Another tab refreshed meanwhile: rt-1 is revoked, rt-2 is current.
    localStorage.setItem(REFRESH_TOKEN_KEY, 'rt-2')

    await expect(refreshSession()).resolves.toBe(true)
    expect(seen).toEqual(['rt-2'])
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('rt-2+1')
  })

  it('takes the shared lock and reads the token only once it holds it', async () => {
    const seen = rotatingRefresh()
    const request = vi.fn(async (_name: string, task: () => Promise<unknown>) => {
      // The tab that held the lock before rotated the token.
      localStorage.setItem(REFRESH_TOKEN_KEY, 'rt-from-other-tab')
      return task()
    })
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })
    setTokens(tokens('rt-1'))

    await expect(refreshSession()).resolves.toBe(true)
    expect(request).toHaveBeenCalledWith('skyline-refresh', expect.any(Function))
    expect(seen).toEqual(['rt-from-other-tab'])
  })

  it('logs out when another tab logs out', async () => {
    setTokens(tokens('rt-1'))
    saveLastPassenger(user.userId, { firstName: 'Ada' })
    const signedOut = vi.fn()
    const unsubscribe = onSessionEnded(signedOut)

    localStorage.removeItem(REFRESH_TOKEN_KEY)
    window.dispatchEvent(new StorageEvent('storage', { key: REFRESH_TOKEN_KEY, oldValue: 'rt-1', newValue: null }))

    expect(getAuthState()).toMatchObject({ status: 'anonymous', accessToken: null })
    expect(readLastPassenger(user.userId)).toEqual({})
    expect(signedOut).toHaveBeenCalledOnce()
    unsubscribe()
  })

  it('adopts a session another tab started', async () => {
    const seen = rotatingRefresh()
    markAnonymous()

    localStorage.setItem(REFRESH_TOKEN_KEY, 'rt-login')
    window.dispatchEvent(new StorageEvent('storage', { key: REFRESH_TOKEN_KEY, newValue: 'rt-login' }))

    await until(() => getAuthState().status === 'authenticated')
    expect(seen).toEqual(['rt-login'])
  })

  it('ignores a rotation by another tab while logged in', () => {
    const seen = rotatingRefresh()
    setTokens(tokens('rt-1'))

    localStorage.setItem(REFRESH_TOKEN_KEY, 'rt-2')
    window.dispatchEvent(new StorageEvent('storage', { key: REFRESH_TOKEN_KEY, newValue: 'rt-2' }))

    expect(seen).toEqual([])
    expect(getAuthState().status).toBe('authenticated')
  })
})

describe('logout', () => {
  it('revokes the refresh token and forgets the passenger prefill', async () => {
    const revoked: string[] = []
    server.use(
      http.post('*/api/v1/auth/logout', async ({ request }) => {
        revoked.push(((await request.json()) as { refreshToken: string }).refreshToken)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    setTokens(tokens('rt-1'))
    saveLastPassenger(user.userId, { firstName: 'Ada', lastName: 'Lovelace' })

    logout()

    expect(getAuthState().status).toBe('anonymous')
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    expect(readLastPassenger(user.userId)).toEqual({})
    await until(() => revoked.length === 1)
    expect(revoked).toEqual(['rt-1'])
  })

  it('logs out even when the server cannot be reached', async () => {
    let called = false
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        called = true
        return HttpResponse.error()
      }),
    )
    setTokens(tokens('rt-1'))

    expect(() => logout()).not.toThrow()
    expect(getAuthState().status).toBe('anonymous')
    await until(() => called)
  })
})

describe('mock back-end', () => {
  it('rotates refresh tokens and refuses a reused one', async () => {
    const first = issueSessionFor('ada@skyline.test')
    const refresh = (refreshToken: string) =>
      rawRequest<{ refreshToken: string }>('/auth/refresh', { method: 'POST', body: { refreshToken } })

    const second = await refresh(first.refreshToken)
    expect(second.refreshToken).not.toBe(first.refreshToken)
    await expect(refresh(first.refreshToken)).rejects.toMatchObject({ status: 401 })
    await expect(refresh(second.refreshToken)).resolves.toBeDefined()
  })
})
