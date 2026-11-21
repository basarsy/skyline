import { api, command } from '../client'
import type {
  AncillaryOptionsResponse,
  AncillaryProductResponse,
  AncillaryPurchaseRequest,
  ReservationResponse,
} from '../types'

/** skyline-reservation: bags and other add-ons. Prices always come from the server. */
export const ancillariesApi = {
  /** The catalog with prices and what each fare includes (used before a reservation exists). */
  catalog: (signal?: AbortSignal) => api.get<AncillaryProductResponse[]>('/ancillary-products', { signal }),
  options: (reservationId: string, signal?: AbortSignal) =>
    api.get<AncillaryOptionsResponse>(`/reservations/${reservationId}/ancillaries/options`, { signal }),
  purchase: (reservationId: string, body: AncillaryPurchaseRequest) =>
    command.post<ReservationResponse>(`/reservations/${reservationId}/ancillaries`, body),
  remove: (reservationId: string, lineId: string) =>
    command.delete<ReservationResponse>(`/reservations/${reservationId}/ancillaries/${lineId}`),
}
