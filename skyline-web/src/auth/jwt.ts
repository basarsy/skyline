import type { UserRole, UUID } from '@/api/types'

/** Claims of a Skyline access token. Decoded client-side for first-paint decisions only; never verified. */
export interface AccessTokenClaims {
  /** Email. */
  sub: string
  userId: UUID
  /** e.g. ["ROLE_PASSENGER"] */
  roles: string[]
  type: 'access' | 'refresh'
  /** Seconds since epoch. */
  iat: number
  exp: number
}

function base64UrlDecode(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** The payload of a JWT, or null when the token is malformed. */
export function decodeJwt(token: string): AccessTokenClaims | null {
  const payload = token.split('.')[1]
  if (!payload) return null
  try {
    const claims = JSON.parse(base64UrlDecode(payload)) as Partial<AccessTokenClaims>
    if (typeof claims.sub !== 'string' || typeof claims.exp !== 'number') return null
    return {
      sub: claims.sub,
      userId: claims.userId ?? '',
      roles: Array.isArray(claims.roles) ? claims.roles : [],
      type: claims.type === 'refresh' ? 'refresh' : 'access',
      iat: claims.iat ?? 0,
      exp: claims.exp,
    }
  } catch {
    return null
  }
}

const ROLES: readonly UserRole[] = ['ADMIN', 'STAFF', 'PASSENGER']

/** The highest Skyline role in the claims, with the "ROLE_" prefix stripped. */
export function roleFromClaims(claims: Pick<AccessTokenClaims, 'roles'>): UserRole | null {
  const names = claims.roles.map((role) => role.replace(/^ROLE_/, ''))
  return ROLES.find((role) => names.includes(role)) ?? null
}

/** Milliseconds until the token expires (negative when already expired). */
export function msUntilExpiry(claims: Pick<AccessTokenClaims, 'exp'>, now = Date.now()) {
  return claims.exp * 1000 - now
}
