import { api, command } from '../client'
import type {
  CabinClass,
  FareType,
  ReservationResponse,
  SeatHoldRequest,
  SeatHoldResponse,
  SeatMapResponse,
} from '../types'

/** While booking: the cabin and fare being bought and the passenger's position. Booked: the reservation. */
export type SeatMapParams = { reservationId: string } | { cabin: CabinClass; fareType: FareType; passenger: number }

/** skyline-reservation: seat maps, holds and seat changes. A seat someone else has is a 409. */
export const seatsApi = {
  seatMap: (flightId: string, params: SeatMapParams, signal?: AbortSignal) =>
    api.get<SeatMapResponse>(`/flights/${flightId}/seat-map`, { query: { ...params }, signal }),

  holdForBooking: (body: SeatHoldRequest) => command.post<SeatHoldResponse>('/reservations/seat-holds', body),
  releaseForBooking: (flightId: string, passenger: number) =>
    command.delete<null>(
      `/reservations/seat-holds?flightId=${encodeURIComponent(flightId)}&passenger=${encodeURIComponent(passenger)}`,
    ),

  holdForReservation: (reservationId: string, seatNumber: string) =>
    command.post<SeatHoldResponse>(`/reservations/${reservationId}/seat-hold`, { seatNumber }),
  releaseForReservation: (reservationId: string) => command.delete<null>(`/reservations/${reservationId}/seat-hold`),
  /** Confirms (and charges) the seat held for this reservation. */
  confirmSeat: (reservationId: string, seatNumber: string) =>
    command.put<ReservationResponse>(`/reservations/${reservationId}/seat`, { seatNumber }),
  removeSeat: (reservationId: string) => command.delete<ReservationResponse>(`/reservations/${reservationId}/seat`),
}
