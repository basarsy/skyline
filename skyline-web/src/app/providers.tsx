import * as React from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { LazyMotion } from 'motion/react'

import { queryClient } from './query-client'

/** Toasts only appear after user actions, so the toaster (sonner) mounts once the page is idle. */
const Toaster = React.lazy(() => import('@/components/ui/sonner').then((module) => ({ default: module.Toaster })))

function IdleToaster() {
  const [ready, setReady] = React.useState(false)
  React.useEffect(() => {
    const start = () => setReady(true)
    if ('requestIdleCallback' in window) {
      const handle = window.requestIdleCallback(start, { timeout: 2000 })
      return () => window.cancelIdleCallback(handle)
    }
    const timer = setTimeout(start, 200)
    return () => clearTimeout(timer)
  }, [])
  return ready ? (
    <React.Suspense fallback={null}>
      <Toaster />
    </React.Suspense>
  ) : null
}

/** Animation features load after first paint; components use the small m.* elements. */
const loadMotionFeatures = () => import('@/lib/motion-features').then((module) => module.default)

/**
 * App-wide providers. Animated components check prefers-reduced-motion themselves and render final
 * states when it is set.
 */
export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <LazyMotion features={loadMotionFeatures} strict>
        {children}
        <IdleToaster />
      </LazyMotion>
    </QueryClientProvider>
  )
}
