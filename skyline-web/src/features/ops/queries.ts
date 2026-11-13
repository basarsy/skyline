import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { crewApi } from '@/api/endpoints/crew'
import { fleetApi } from '@/api/endpoints/fleet'
import { networkApi } from '@/api/endpoints/network'
import { scheduleApi } from '@/api/endpoints/schedule'
import { usersApi, type UserSearchParams } from '@/api/endpoints/users'
import { crewKeys, fleetKeys, flightKeys, networkKeys, reservationKeys, userKeys } from '@/api/query-keys'
import type {
  AircraftRequest,
  AircraftResponse,
  AircraftStatus,
  AircraftTypeRequest,
  AirportRequest,
  CreateUserRequest,
  CrewMemberRequest,
  CrewRole,
  FlightBoardParams,
  FlightCrewAssignmentRequest,
  FlightRequest,
  FlightStatus,
  RouteRequest,
} from '@/api/types'
import { STATIC_STALE_TIME } from '@/app/query-client'

// ------------------------------------------------------------------ flights

export function useFlightBoard(params: FlightBoardParams) {
  return useQuery({
    queryKey: flightKeys.board(params),
    queryFn: ({ signal }) => scheduleApi.board(params, signal),
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: true,
  })
}

/** Status changes invalidate the flight and the board. */
export function useSetFlightStatus(flightId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (status: FlightStatus) => scheduleApi.setStatus(flightId, status),
    onSuccess: async (result) => {
      queryClient.setQueryData(flightKeys.detail(flightId), result.data)
      toast.success(result.message ?? 'Flight status updated')
      await queryClient.invalidateQueries({ queryKey: flightKeys.all })
    },
  })
}

/** Cancelling invalidates the flight, the board and every reservation (bookings are cancelled via Kafka). */
export function useCancelFlight(flightId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => scheduleApi.cancel(flightId),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Flight cancelled', {
        description: 'Passengers’ bookings are being cancelled; this takes a few seconds.',
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: flightKeys.all }),
        queryClient.invalidateQueries({ queryKey: reservationKeys.all }),
      ])
    },
  })
}

export function useSaveFlight(flightId?: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: FlightRequest) => (flightId ? scheduleApi.update(flightId, body) : scheduleApi.create(body)),
    onSuccess: async (result) => {
      queryClient.setQueryData(flightKeys.detail(result.data.id), result.data)
      toast.success(result.message ?? (flightId ? 'Flight updated' : 'Flight created'))
      await queryClient.invalidateQueries({ queryKey: flightKeys.all })
    },
  })
}

// ------------------------------------------------------------------ fleet

export function useAircraft() {
  return useQuery({ queryKey: fleetKeys.aircraft, queryFn: ({ signal }) => fleetApi.aircraft(signal) })
}

export function useAircraftTypes() {
  return useQuery({
    queryKey: fleetKeys.types,
    queryFn: ({ signal }) => fleetApi.types(signal),
    staleTime: STATIC_STALE_TIME,
  })
}

export function useCreateAircraft() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AircraftRequest) => fleetApi.createAircraft(body),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Aircraft added')
      await queryClient.invalidateQueries({ queryKey: fleetKeys.aircraft })
    },
  })
}

/** The one optimistic update: the status flips immediately and rolls back if the server refuses. */
export function useSetAircraftStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: AircraftStatus }) => fleetApi.setAircraftStatus(id, status),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey: fleetKeys.aircraft })
      const previous = queryClient.getQueryData<AircraftResponse[]>(fleetKeys.aircraft)
      queryClient.setQueryData<AircraftResponse[]>(fleetKeys.aircraft, (list) =>
        list?.map((aircraft) => (aircraft.id === id ? { ...aircraft, status } : aircraft)),
      )
      return { previous }
    },
    onError: (error, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(fleetKeys.aircraft, context.previous)
      toast.error(error.message)
    },
    onSuccess: (result) => toast.success(result.message ?? 'Aircraft status updated'),
    onSettled: () => queryClient.invalidateQueries({ queryKey: fleetKeys.aircraft }),
  })
}

export function useCreateAircraftType() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AircraftTypeRequest) => fleetApi.createType(body),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Aircraft type added')
      await queryClient.invalidateQueries({ queryKey: fleetKeys.types })
    },
  })
}

// ------------------------------------------------------------------ network

export function useCreateAirport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AirportRequest) => networkApi.createAirport(body),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Airport added')
      await queryClient.invalidateQueries({ queryKey: networkKeys.airports })
    },
  })
}

export function useCreateRoute() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: RouteRequest) => networkApi.createRoute(body),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Route added')
      await queryClient.invalidateQueries({ queryKey: networkKeys.routes })
    },
  })
}

// ------------------------------------------------------------------ crew

export function useCrewList(params: { role?: CrewRole; q?: string }) {
  return useQuery({
    queryKey: crewKeys.list(params),
    queryFn: ({ signal }) => crewApi.list(params, signal),
    placeholderData: keepPreviousData,
  })
}

export function useManifest(flightId: string) {
  return useQuery({
    queryKey: crewKeys.manifest(flightId),
    queryFn: ({ signal }) => crewApi.manifest(flightId, signal),
  })
}

export function useCreateCrewMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CrewMemberRequest) => crewApi.create(body),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Crew member added')
      await queryClient.invalidateQueries({ queryKey: crewKeys.all })
    },
  })
}

/** Crew changes invalidate the manifest and the crew list. */
export function useAssignCrew(flightId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: FlightCrewAssignmentRequest) => crewApi.assign(flightId, body),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Crew assigned')
      await queryClient.invalidateQueries({ queryKey: crewKeys.all })
    },
  })
}

export function useRemoveCrew(flightId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (crewMemberId: string) => crewApi.remove(flightId, crewMemberId),
    onSuccess: async (result) => {
      toast.success(result.message ?? 'Crew member removed')
      await queryClient.invalidateQueries({ queryKey: crewKeys.all })
    },
  })
}

// ------------------------------------------------------------------ users

export function useUserSearch(params: UserSearchParams, enabled = true) {
  return useQuery({
    queryKey: userKeys.search(params),
    queryFn: ({ signal }) => usersApi.search(params, signal),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useCreateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateUserRequest) => usersApi.create(body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: userKeys.all })
    },
  })
}
