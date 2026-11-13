import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { checkinApi } from '@/api/endpoints/checkin'
import { reservationsApi } from '@/api/endpoints/reservations'
import { checkinKeys, flightKeys, reservationKeys } from '@/api/query-keys'
import type { ReservationResponse } from '@/api/types'

export const TRIPS_PAGE_SIZE = 50

export function useMyReservations() {
  return useQuery({
    queryKey: reservationKeys.my({ size: TRIPS_PAGE_SIZE }),
    // Newest bookings first; the Upcoming/Past/Cancelled split is computed client-side.
    queryFn: ({ signal }) => reservationsApi.mine({ size: TRIPS_PAGE_SIZE, sort: 'bookedAt,desc' }, signal),
    refetchOnWindowFocus: true,
  })
}

export function useReservation(id: string | undefined) {
  return useQuery({
    queryKey: reservationKeys.detail(id ?? ''),
    queryFn: ({ signal }) => reservationsApi.byId(id!, signal),
    enabled: !!id,
    refetchOnWindowFocus: true,
  })
}

/** PNR lookup. With the owner's token the view is full and lastName optional; otherwise redacted. */
export function useReservationByPnr(pnr: string | undefined, lastName: string | undefined, enabled = true) {
  return useQuery({
    queryKey: reservationKeys.pnr(pnr ?? '', lastName),
    queryFn: ({ signal }) => reservationsApi.byPnr(pnr!, lastName, signal),
    enabled: !!pnr && enabled,
    retry: false,
  })
}

export function useBoardingPass(pnr: string | undefined, enabled = true) {
  return useQuery({
    queryKey: checkinKeys.boardingPass(pnr ?? ''),
    queryFn: ({ signal }) => checkinApi.boardingPass(pnr!, signal),
    enabled: !!pnr && enabled,
    retry: false,
  })
}

/** Cancelling invalidates every reservation query and the flight (its seat count goes back up). */
export function useCancelReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (reservation: ReservationResponse) => reservationsApi.cancel(reservation.id),
    onSuccess: async (result, reservation) => {
      toast.success(result.message ?? 'Booking cancelled')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: reservationKeys.all }),
        reservation.flight && queryClient.invalidateQueries({ queryKey: flightKeys.detail(reservation.flight.id) }),
        queryClient.invalidateQueries({ queryKey: flightKeys.all, refetchType: 'none' }),
      ])
    },
  })
}
