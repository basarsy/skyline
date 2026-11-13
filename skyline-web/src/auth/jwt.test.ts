import { describe, expect, it } from 'vitest'

import { fakeJwt } from '@/test/fixtures'
import { decodeJwt, msUntilExpiry, roleFromClaims } from './jwt'

describe('decodeJwt', () => {
  it('decodes the Skyline claim shape', () => {
    const token = fakeJwt({ sub: 'ops@skyline.test', userId: 'u-1', roles: ['ROLE_STAFF'], expInSeconds: 600 })

    const claims = decodeJwt(token)

    expect(claims).toMatchObject({ sub: 'ops@skyline.test', userId: 'u-1', roles: ['ROLE_STAFF'], type: 'access' })
    expect(msUntilExpiry(claims!)).toBeGreaterThan(590_000)
  })

  it('handles base64url characters and UTF-8', () => {
    const payload = btoa(
      String.fromCharCode(
        ...new TextEncoder().encode(JSON.stringify({ sub: 'şükrü@örnek.tr', exp: 1, userId: '?>?>' })),
      ),
    )
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')

    expect(decodeJwt(`x.${payload}.y`)?.sub).toBe('şükrü@örnek.tr')
  })

  it('returns null for malformed tokens', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull()
    expect(decodeJwt('a.%%%.c')).toBeNull()
    expect(decodeJwt(`a.${btoa('{"no":"sub"}')}.c`)).toBeNull()
  })
})

describe('roleFromClaims', () => {
  it('strips the ROLE_ prefix and picks the highest role', () => {
    expect(roleFromClaims({ roles: ['ROLE_PASSENGER'] })).toBe('PASSENGER')
    expect(roleFromClaims({ roles: ['ROLE_STAFF', 'ROLE_ADMIN'] })).toBe('ADMIN')
    expect(roleFromClaims({ roles: ['ROLE_SERVICE'] })).toBeNull()
  })
})
