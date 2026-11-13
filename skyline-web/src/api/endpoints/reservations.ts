import { api, command } from '../client'
import { parsePage } from '../page'
import type { PageParams, ReservationRequest, ReservationResponse } from '../types'

/** skyline-reservation: bookings. */
export const reservationsApi = {
  book: (body: ReservationRequest) => command.post<ReservationResponse>('/reservations', body),
  byId: (id: string, signal?: AbortSignal) => api.get<ReservationResponse>(`/reservations/${id}`, { signal }),
  /** Full view with the owner's (or staff) token; otherwise lastName is required and the view is redacted. */
  byPnr: (pnr: string, lastName?: string, signal?: AbortSignal) =>
    api.get<ReservationResponse>(`/reservations/pnr/${encodeURIComponent(pnr)}`, { query: { lastName }, signal }),
  mine: async (params: PageParams, signal?: AbortSignal) =>
    parsePage<ReservationResponse>(await api.get('/reservations/my', { query: { ...params }, signal })),
  cancel: (id: string) => command.delete<null>(`/reservations/${id}`),
}
