import * as React from 'react'
import { Outlet, ScrollRestoration, useLocation, useNavigate } from 'react-router'

import { onSessionExpired } from '@/auth/auth-store'
import { queryClient } from './query-client'

/**
 * Root of every route: restores scroll, and when a session can't be refreshed any more, clears cached
 * data and sends the user to log in again, returning here afterwards.
 */
export function RootLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const locationRef = React.useRef(location)

  React.useEffect(() => {
    locationRef.current = location
  }, [location])

  React.useEffect(
    () =>
      onSessionExpired(() => {
        queryClient.clear()
        const { pathname, search } = locationRef.current
        void import('sonner').then(({ toast }) => toast('Your session has ended. Please log in again.'))
        navigate(`/login?next=${encodeURIComponent(`${pathname}${search}`)}`, { replace: true })
      }),
    [navigate],
  )

  return (
    <>
      <ScrollRestoration />
      <Outlet />
    </>
  )
}

/** Blank frame with the page background while the first route chunk loads (no layout flash). */
export function BootFallback() {
  return (
    <div role="status" aria-live="polite" className="min-h-dvh bg-background">
      <span className="sr-only">Loading Skyline</span>
    </div>
  )
}
