import { api, command } from '../client'
import type {
  AircraftRequest,
  AircraftResponse,
  AircraftStatus,
  AircraftTypeRequest,
  AircraftTypeResponse,
} from '../types'

/** skyline-fleet: aircraft and aircraft types (STAFF, ADMIN). */
export const fleetApi = {
  aircraft: (signal?: AbortSignal) => api.get<AircraftResponse[]>('/aircraft', { signal }),
  aircraftById: (id: string, signal?: AbortSignal) => api.get<AircraftResponse>(`/aircraft/${id}`, { signal }),
  createAircraft: (body: AircraftRequest) => command.post<AircraftResponse>('/aircraft', body),
  setAircraftStatus: (id: string, status: AircraftStatus) =>
    command.patch<AircraftResponse>(`/aircraft/${id}/status`, { status }),
  types: (signal?: AbortSignal) => api.get<AircraftTypeResponse[]>('/aircraft-types', { signal }),
  createType: (body: AircraftTypeRequest) => command.post<AircraftTypeResponse>('/aircraft-types', body),
}
