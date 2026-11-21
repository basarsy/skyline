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

  useFocusOnNavigation(location.pathname)

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

/**
 * After a client-side navigation, moves focus to the new page's heading (or its main landmark), as a full page
 * load would reset it, so keyboard and screen-reader users start at the new content. Keyed on the path only:
 * query changes (filters, the board's search box) keep focus where it is. Skips the first render, and pages
 * that already put focus inside main (an autofocused field). preventScroll leaves scroll to ScrollRestoration.
 */
function useFocusOnNavigation(pathname: string) {
  const firstRender = React.useRef(true)
  React.useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    const main = document.querySelector<HTMLElement>('main')
    if (!main || main.contains(document.activeElement)) return
    const heading = main.querySelector<HTMLElement>('h1')
    const target = heading ?? main
    if (heading && !heading.hasAttribute('tabindex')) {
      // Focusable by script only, and without a ring: like main, it's a starting point, not a control.
      heading.tabIndex = -1
      heading.classList.add('outline-none')
    }
    target.focus({ preventScroll: true })
  }, [pathname])
}

/** Blank frame with the page background while the first route chunk loads (no layout flash). */
export function BootFallback() {
  return (
    <div role="status" aria-live="polite" className="min-h-dvh bg-background">
      <span className="sr-only">Loading Skyline</span>
    </div>
  )
}
