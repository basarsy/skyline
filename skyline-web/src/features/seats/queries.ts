import { useQuery } from '@tanstack/react-query'

import { ancillariesApi } from '@/api/endpoints/ancillaries'
import { seatsApi, type SeatMapParams } from '@/api/endpoints/seats'
import { isApiError } from '@/api/http'
import { ancillaryKeys, seatKeys } from '@/api/query-keys'

/**
 * A flight's seat map for one passenger. Kept fresh (seats come and go while people choose), and a 404 means the
 * aircraft has no seat map: the caller then explains that a seat is assigned at check-in.
 */
export function useSeatMap(flightId: string | undefined, params: SeatMapParams, enabled = true) {
  return useQuery({
    queryKey: seatKeys.map(flightId ?? '', params as Record<string, string | number>),
    queryFn: ({ signal }) => seatsApi.seatMap(flightId!, params, signal),
    enabled: Boolean(flightId) && enabled,
    staleTime: 10_000,
    refetchInterval: 30_000,
    retry: (count, error) => !(isApiError(error) && error.status < 500) && count < 2,
  })
}

export function useAncillaryCatalog() {
  return useQuery({
    queryKey: ancillaryKeys.catalog,
    queryFn: ({ signal }) => ancillariesApi.catalog(signal),
    staleTime: 5 * 60_000,
  })
}

export function useAncillaryOptions(reservationId: string | undefined) {
  return useQuery({
    queryKey: ancillaryKeys.options(reservationId ?? ''),
    queryFn: ({ signal }) => ancillariesApi.options(reservationId!, signal),
    enabled: Boolean(reservationId),
  })
}

/** "No seat map for this aircraft" (or the flight has none yet): seats are then assigned at check-in. */
export function isNoSeatMap(error: unknown) {
  return isApiError(error) && error.status === 404
}
