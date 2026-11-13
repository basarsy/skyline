import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { getAccessToken, getAuthState, REFRESH_TOKEN_KEY, setTokens } from '@/auth/auth-store'
import { fakeJwt } from '@/test/fixtures'
import { fail, ok } from '@/test/handlers'
import { server } from '@/test/server'
import { ApiError, apiFetch } from './client'
import { parseFieldErrors } from './http'

const user = { sub: 'ada@skyline.test', userId: 'e0000000-0000-4000-8000-000000000001', roles: ['ROLE_PASSENGER'] }

describe('apiFetch envelope handling', () => {
  it('unwraps data on 2xx', async () => {
    server.use(http.get('*/api/v1/things', () => ok({ answer: 42 }, 'Things retrieved')))

    await expect(apiFetch('/things')).resolves.toEqual({ answer: 42 })
  })

  it('returns undefined for an empty 2xx body', async () => {
    server.use(http.put('*/api/v1/things', () => new HttpResponse(null, { status: 200 })))

    await expect(apiFetch('/things', { method: 'PUT' })).resolves.toBeUndefined()
  })

  it('throws a typed ApiError with the server message', async () => {
    server.use(
      http.get('*/api/v1/things', () => fail(409, 'Concurrent update conflict. Please refresh and try again.')),
    )

    const error = await apiFetch('/things').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, message: 'Concurrent update conflict. Please refresh and try again.' })
  })

  it('maps validation errors onto field paths, nested ones included', async () => {
    server.use(
      http.post('*/api/v1/reservations', () =>
        fail(400, 'Validation failed', [
          'passenger.lastName: must not be blank',
          'cabinClass: must not be null',
          'passenger.lastName: second message is ignored',
        ]),
      ),
    )

    const error = (await apiFetch('/reservations', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError
    expect(error.status).toBe(400)
    expect(error.message).toBe('Validation failed')
    expect(error.fieldErrors).toEqual({
      'passenger.lastName': 'must not be blank',
      cabinClass: 'must not be null',
    })
  })

  it('falls back to a readable message when the body is not an envelope', async () => {
    server.use(http.get('*/api/v1/things', () => new HttpResponse('<html>Bad gateway</html>', { status: 502 })))

    await expect(apiFetch('/things')).rejects.toMatchObject({
      status: 502,
      message: expect.stringContaining('Something went wrong'),
    })
  })

  it('reports an unreachable network as status 0', async () => {
    server.use(http.get('*/api/v1/things', () => HttpResponse.error()))

    await expect(apiFetch('/things')).rejects.toMatchObject({ status: 0 })
  })

  it('sends query arrays as repeated parameters', async () => {
    let seen: string[] = []
    server.use(
      http.get('*/api/v1/flights/board', ({ request }) => {
        seen = new URL(request.url).searchParams.getAll('status')
        return ok([])
      }),
    )

    await apiFetch('/flights/board', { query: { status: ['DELAYED', 'BOARDING'], empty: '', missing: undefined } })
    expect(seen).toEqual(['DELAYED', 'BOARDING'])
  })
})

describe('parseFieldErrors', () => {
  it('splits on the first ": " only', () => {
    expect(parseFieldErrors(['note: a: b', 'broken entry', ': no field'])).toEqual({ note: 'a: b' })
  })
})

describe('authentication and refresh', () => {
  it('attaches the bearer token when logged in', async () => {
    let authorization: string | null = null
    server.use(
      http.get('*/api/v1/things', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return ok(null)
      }),
    )
    const accessToken = fakeJwt(user)
    setTokens({ accessToken, refreshToken: 'rt-1', tokenType: 'Bearer', expiresIn: 900 })

    await apiFetch('/things')
    expect(authorization).toBe(`Bearer ${accessToken}`)
  })

  it('refreshes once for concurrent 401s and replays each request once', async () => {
    const expired = fakeJwt({ ...user, expInSeconds: 1 })
    const fresh = fakeJwt({ ...user, expInSeconds: 900 })
    let refreshCalls = 0
    const replays: string[] = []

    server.use(
      http.post('*/api/v1/auth/refresh', async ({ request }) => {
        refreshCalls += 1
        const body = (await request.json()) as { refreshToken: string }
        expect(body.refreshToken).toBe('rt-old')
        await new Promise((resolve) => setTimeout(resolve, 20))
        return ok({ accessToken: fresh, refreshToken: 'rt-new', tokenType: 'Bearer', expiresIn: 900 })
      }),
      http.get('*/api/v1/things/:n', ({ request, params }) => {
        if (request.headers.get('Authorization') !== `Bearer ${fresh}`) return fail(401, 'Authentication required')
        replays.push(String(params.n))
        return ok({ n: Number(params.n) })
      }),
    )
    setTokens({ accessToken: expired, refreshToken: 'rt-old', tokenType: 'Bearer', expiresIn: 1 })

    const results = await Promise.all([1, 2, 3].map((n) => apiFetch<{ n: number }>(`/things/${n}`)))

    expect(results.map((r) => r.n)).toEqual([1, 2, 3])
    expect(refreshCalls).toBe(1)
    expect(replays.sort()).toEqual(['1', '2', '3'])
    expect(getAccessToken()).toBe(fresh)
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('rt-new')
  })

  it('logs out when the refresh fails, and surfaces the 401', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () => fail(401, 'Invalid refresh token')),
      http.get('*/api/v1/things', () => fail(401, 'Authentication required')),
    )
    setTokens({ accessToken: fakeJwt(user), refreshToken: 'rt-dead', tokenType: 'Bearer', expiresIn: 900 })

    await expect(apiFetch('/things')).rejects.toMatchObject({ status: 401 })
    expect(getAuthState().status).toBe('anonymous')
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
  })

  it('does not try to refresh after a failed login', async () => {
    let refreshCalls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls += 1
        return ok(null)
      }),
      http.post('*/api/v1/auth/login', () => fail(401, 'Invalid email or password')),
    )
    setTokens({ accessToken: fakeJwt(user), refreshToken: 'rt', tokenType: 'Bearer', expiresIn: 900 })

    await expect(apiFetch('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({
      message: 'Invalid email or password',
    })
    expect(refreshCalls).toBe(0)
  })
})
