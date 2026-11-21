import { useState } from 'react'
import { useQueries } from '@tanstack/react-query'

import { scheduleApi } from '@/api/endpoints/schedule'
import { flightKeys } from '@/api/query-keys'
import type { RouteResponse } from '@/api/types'
import { isBookable } from '@/lib/flight-status'
import { addIsoDays, todayIn } from '@/lib/format'
import { estimatePrice } from '@/lib/pricing'

/*
 * The home page's lowest-fare scan. Kept apart from ./queries so the results page doesn't load it.
 * Flight search only takes one day at a time, so this costs one request per route and day. Until the API has a
 * lowest-fare endpoint, the scan is bounded: at most MAX_SCANNED_ROUTES routes, MAX_CONCURRENT requests at once.
 */

/** How many days ahead the home page looks for the lowest fare on a route. */
export const FARE_SCAN_DAYS = 7
const MAX_SCANNED_ROUTES = 8
const MAX_CONCURRENT = 4

export interface LowestFare {
  amount: number
  currency: string | null | undefined
}

export type FareResult =
  | { state: 'loading' }
  /** No bookable flight in the window. */
  | { state: 'none' }
  /** A lookup failed or the route wasn't scanned: there is no trustworthy figure to show. */
  | { state: 'unknown' }
  | ({ state: 'found' } & LowestFare)

let active = 0
const waiting: (() => void)[] = []

/** Runs `task` once one of MAX_CONCURRENT slots is free; a slot passes straight to the next waiter. */
async function limited<T>(signal: AbortSignal, task: () => Promise<T>): Promise<T> {
  if (active < MAX_CONCURRENT) active += 1
  else {
    await new Promise<void>((resolve, reject) => {
      const start = () => {
        signal.removeEventListener('abort', cancel)
        resolve()
      }
      const cancel = () => {
        waiting.splice(waiting.indexOf(start), 1)
        reject(signal.reason)
      }
      waiting.push(start)
      signal.addEventListener('abort', cancel, { once: true })
    })
  }
  try {
    return await task()
  } finally {
    const next = waiting.shift()
    if (next) next()
    else active -= 1
  }
}

/** Scan days per mount (`now`) and origin zone, so re-renders don't redo the zone conversions. */
const scanDaysCache = new WeakMap<Date, Map<string, string[]>>()

/** The FARE_SCAN_DAYS days from `now`'s date in `zone` on ("yyyy-MM-dd"), computed once per `now` and zone. */
function scanDays(zone: string, now: Date) {
  let byZone = scanDaysCache.get(now)
  if (!byZone) scanDaysCache.set(now, (byZone = new Map()))
  let days = byZone.get(zone)
  if (!days) {
    const today = todayIn(zone, now)
    days = Array.from({ length: FARE_SCAN_DAYS }, (_, i) => addIsoDays(today, i))
    byZone.set(zone, days)
  }
  return days
}

/**
 * The lowest estimated Economy fare over the next FARE_SCAN_DAYS days at each route's origin (search dates are
 * the origin airport's calendar days), for a set of routes. Queries run only while `enabled` (the cards are on
 * screen).
 */
export function useLowestFares(routes: RouteResponse[], enabled: boolean) {
  // The window is fixed when the page mounts.
  const [now] = useState(() => new Date())
  const scanned = routes.slice(0, MAX_SCANNED_ROUTES)
  const pairs = scanned.flatMap((route) => scanDays(route.origin.timezone, now).map((date) => ({ route, date })))
  const results = useQueries({
    queries: pairs.map(({ route, date }) => ({
      queryKey: flightKeys.fareDay(route.origin.iataCode, route.destination.iataCode, date),
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        limited(signal, () =>
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
        ),
      enabled,
      staleTime: 5 * 60_000,
    })),
  })

  const daysByRoute = new Map(
    scanned.map((route, index) => [route.id, results.slice(index * FARE_SCAN_DAYS, (index + 1) * FARE_SCAN_DAYS)]),
  )

  /** The lowest fare over several routes (e.g. every direct route into one airport). */
  return (subset: RouteResponse[]): FareResult => {
    const slices = subset.map((route) => daysByRoute.get(route.id))
    // Unscanned routes and failed lookups alike leave the minimum unknown: a partial one could be wrong.
    if (slices.some((slice) => !slice || slice.some((result) => result.isError))) return { state: 'unknown' }
    const all = slices.flatMap((slice) => slice ?? [])
    if (all.some((result) => result.isPending)) return { state: 'loading' }
    let best: FareResult = { state: 'none' }
    for (const flight of all.flatMap((result) => result.data?.content ?? [])) {
      if (!isBookable(flight)) continue
      const amount = estimatePrice(flight, 'ECONOMY').amount
      if (best.state !== 'found' || amount < best.amount) best = { state: 'found', amount, currency: flight.currency }
    }
    return best
  }
}
