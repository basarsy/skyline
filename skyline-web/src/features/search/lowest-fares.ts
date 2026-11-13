import { useState } from 'react'
import { useQueries } from '@tanstack/react-query'

import { scheduleApi } from '@/api/endpoints/schedule'
import { flightKeys } from '@/api/query-keys'
import type { RouteResponse } from '@/api/types'
import { isBookable } from '@/lib/flight-status'
import { toUtcDay } from '@/lib/format'
import { estimatePrice } from '@/lib/pricing'

/* The home page's lowest-fare scan. Kept apart from ./queries so the results page doesn't load it. */

/** How many days ahead the home page looks for the lowest fare on a route. */
export const FARE_SCAN_DAYS = 7

export interface LowestFare {
  amount: number
  currency: string | null | undefined
}

/**
 * The lowest estimated Economy fare per route over the next FARE_SCAN_DAYS UTC days. Flight search only takes
 * one day at a time, so this runs one small query per route and day, and only while `enabled` (the cards are
 * on screen). Routes with no bookable flight in the window map to null; undefined means still loading.
 */
export function useLowestFares(routes: RouteResponse[], enabled: boolean) {
  // The window is fixed when the page mounts.
  const [days] = useState(() =>
    Array.from({ length: FARE_SCAN_DAYS }, (_, i) => toUtcDay(new Date(Date.now() + i * 86_400_000))),
  )
  const pairs = routes.flatMap((route) => days.map((date) => ({ route, date })))
  const results = useQueries({
    queries: pairs.map(({ route, date }) => ({
      queryKey: flightKeys.fareDay(route.origin.iataCode, route.destination.iataCode, date),
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        scheduleApi.search(
          {
            origin: route.origin.iataCode,
            destination: route.destination.iataCode,
            date,
            page: 0,
            size: 20,
            sort: 'departureTime,asc',
          },
          signal,
        ),
      enabled,
      staleTime: 5 * 60_000,
    })),
  })

  const fares = new Map<string, LowestFare | null | undefined>()
  routes.forEach((route, index) => {
    const slice = results.slice(index * days.length, (index + 1) * days.length)
    if (slice.some((result) => result.isPending)) return fares.set(route.id, undefined)
    let best: LowestFare | null = null
    for (const result of slice) {
      for (const flight of result.data?.content ?? []) {
        if (!isBookable(flight)) continue
        const amount = estimatePrice(flight, 'ECONOMY').amount
        if (!best || amount < best.amount) best = { amount, currency: flight.currency }
      }
    }
    fares.set(route.id, best)
  })
  return fares
}
