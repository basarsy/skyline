import { QueryClient } from '@tanstack/react-query'

import { isApiError } from '@/api/http'

/** Statuses worth retrying a GET for: unreachable (0), busy (503), and transient server errors. */
const RETRYABLE = new Set([0, 502, 503, 504])

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // GETs retry twice with backoff, but only when retrying can help.
        retry: (failureCount, error) =>
          failureCount < 2 && (!isApiError(error) || RETRYABLE.has(error.status) || error.status >= 500),
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
      },
      // Mutations never retry automatically.
      mutations: { retry: false },
    },
  })
}

export const queryClient = createQueryClient()

/** Airports, routes and aircraft types change rarely. */
export const STATIC_STALE_TIME = 10 * 60_000
