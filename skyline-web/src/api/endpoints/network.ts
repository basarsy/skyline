import { api, command } from '../client'
import type { AirportRequest, AirportResponse, RouteRequest, RouteResponse } from '../types'

/** skyline-route: airports and routes (reads are public). */
export const networkApi = {
  airports: (signal?: AbortSignal) => api.get<AirportResponse[]>('/airports', { signal }),
  airport: (id: string, signal?: AbortSignal) => api.get<AirportResponse>(`/airports/${id}`, { signal }),
  createAirport: (body: AirportRequest) => command.post<AirportResponse>('/airports', body),
  routes: (signal?: AbortSignal) => api.get<RouteResponse[]>('/routes', { signal }),
  route: (id: string, signal?: AbortSignal) => api.get<RouteResponse>(`/routes/${id}`, { signal }),
  searchRoutes: (originIata: string, destinationIata: string, signal?: AbortSignal) =>
    api.get<RouteResponse[]>('/routes/search', { query: { originIata, destinationIata }, signal }),
  createRoute: (body: RouteRequest) => command.post<RouteResponse>('/routes', body),
}
