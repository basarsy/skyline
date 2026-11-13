import type * as React from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'

import type { UserRole } from '@/api/types'
import { ForbiddenState } from '@/components/skyline/states'
import { Skeleton } from '@/components/ui/skeleton'
import { homeFor, safeNext } from './session'
import { useAuth } from './use-auth'

/** Shown while the session is being restored on boot (refresh + /me). */
function SessionPending() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="mx-auto flex w-full max-w-[1200px] flex-col gap-4 px-4 py-12 sm:px-6"
    >
      <span className="sr-only">Loading your session</span>
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-40 w-full rounded-card" />
    </div>
  )
}

/** Logged-in users only; everyone else goes to /login?next=<here>. */
export function RequireAuth({ children }: { children?: React.ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <SessionPending />
  if (status === 'anonymous') {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />
  }
  return children ?? <Outlet />
}

/** One of the roles; logged-in users without it see the 403 state. */
export function RequireRole({ roles, children }: { roles: UserRole[]; children?: React.ReactNode }) {
  const { hasRole, status } = useAuth()
  return (
    <RequireAuth>
      {status === 'authenticated' && !hasRole(...roles) ? <ForbiddenState /> : (children ?? <Outlet />)}
    </RequireAuth>
  )
}

/** /login and /register: a logged-in user is sent on (to `next` or their landing page). */
export function RedirectIfAuthenticated({ children }: { children?: React.ReactNode }) {
  const { status, role } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <SessionPending />
  if (status === 'authenticated') {
    const next = safeNext(new URLSearchParams(location.search).get('next'))
    return <Navigate to={next ?? homeFor(role)} replace />
  }
  return children ?? <Outlet />
}
