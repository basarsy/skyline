import { api, command } from '../client'
import { parsePage } from '../page'
import type { FlightBoardParams, FlightRequest, FlightResponse, FlightStatus, PageParams } from '../types'

export interface FlightSearchParams extends PageParams {
  /** IATA */
  origin: string
  destination: string
  /** A calendar day at the origin airport (its time zone), yyyy-MM-dd. */
  date: string
}

/** skyline-schedule: flights. */
export const scheduleApi = {
  search: async (params: FlightSearchParams, signal?: AbortSignal) =>
    parsePage<FlightResponse>(await api.get('/flights', { query: { ...params }, signal })),
  board: async (params: FlightBoardParams, signal?: AbortSignal) =>
    parsePage<FlightResponse>(await api.get('/flights/board', { query: { ...params }, signal })),
  flight: (id: string, signal?: AbortSignal) => api.get<FlightResponse>(`/flights/${id}`, { signal }),
  create: (body: FlightRequest) => command.post<FlightResponse>('/flights', body),
  update: (id: string, body: FlightRequest) => command.put<FlightResponse>(`/flights/${id}`, body),
  setStatus: (id: string, status: FlightStatus) => command.patch<FlightResponse>(`/flights/${id}/status`, { status }),
  /** Sets the expected departure (UTC); the flight becomes DELAYED. STAFF/ADMIN. */
  delay: (id: string, estimatedDepartureTime: string) =>
    command.put<FlightResponse>(`/flights/${id}/delay`, { estimatedDepartureTime }),
  /** Lifts the delay: a DELAYED flight is SCHEDULED again. */
  clearDelay: (id: string) => command.delete<FlightResponse>(`/flights/${id}/delay`),
  setGate: (id: string, gate: string) => command.put<FlightResponse>(`/flights/${id}/gate`, { gate }),
  /** Cancels (does not delete); bookings are cancelled asynchronously. ADMIN. */
  cancel: (id: string) => command.delete<null>(`/flights/${id}`),
}
