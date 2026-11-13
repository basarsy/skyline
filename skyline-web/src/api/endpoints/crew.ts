import { api, command } from '../client'
import type {
  CrewMemberRequest,
  CrewMemberResponse,
  CrewRole,
  FlightCrewAssignmentRequest,
  FlightCrewResponse,
} from '../types'

/** skyline-crew: crew directory and flight manifests (STAFF, ADMIN). */
export const crewApi = {
  list: (params: { role?: CrewRole; q?: string }, signal?: AbortSignal) =>
    api.get<CrewMemberResponse[]>('/crew', { query: { ...params }, signal }),
  byId: (id: string, signal?: AbortSignal) => api.get<CrewMemberResponse>(`/crew/${id}`, { signal }),
  create: (body: CrewMemberRequest) => command.post<CrewMemberResponse>('/crew', body),
  manifest: (flightId: string, signal?: AbortSignal) =>
    api.get<FlightCrewResponse[]>(`/flights/${flightId}/crew`, { signal }),
  assign: (flightId: string, body: FlightCrewAssignmentRequest) =>
    command.post<FlightCrewResponse>(`/flights/${flightId}/crew`, body),
  remove: (flightId: string, crewMemberId: string) => command.delete<null>(`/flights/${flightId}/crew/${crewMemberId}`),
}
