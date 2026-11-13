import type { FlightSearchParams } from './endpoints/schedule'
import type { CrewRole, FlightBoardParams, UserRole } from './types'

/** Query-key factories. Every key starts with its area, so invalidation can be broad or precise. */
export const authKeys = {
  me: ['auth', 'me'] as const,
}

export const userKeys = {
  all: ['users'] as const,
  search: (params: { email?: string; role?: UserRole; page?: number }) => ['users', 'search', params] as const,
}

export const networkKeys = {
  airports: ['network', 'airports'] as const,
  routes: ['network', 'routes'] as const,
}

export const fleetKeys = {
  all: ['fleet'] as const,
  aircraft: ['fleet', 'aircraft'] as const,
  types: ['fleet', 'types'] as const,
}

export const flightKeys = {
  all: ['flights'] as const,
  search: (params: Omit<FlightSearchParams, 'page' | 'size'>) => ['flights', 'search', params] as const,
  boardAll: ['flights', 'board'] as const,
  board: (params: FlightBoardParams) => ['flights', 'board', params] as const,
  detail: (id: string) => ['flights', 'detail', id] as const,
  /** One day of the home page's lowest-fare scan (first page, earliest first). */
  fareDay: (origin: string, destination: string, date: string) =>
    ['flights', 'fare-day', origin, destination, date] as const,
}

export const reservationKeys = {
  all: ['reservations'] as const,
  myAll: ['reservations', 'my'] as const,
  my: (params: { size?: number }) => ['reservations', 'my', params] as const,
  detail: (id: string) => ['reservations', 'detail', id] as const,
  pnr: (pnr: string, lastName?: string) => ['reservations', 'pnr', pnr, lastName ?? null] as const,
}

export const checkinKeys = {
  boardingPass: (pnr: string) => ['checkin', 'boarding-pass', pnr] as const,
}

export const crewKeys = {
  all: ['crew'] as const,
  list: (params: { role?: CrewRole; q?: string }) => ['crew', 'list', params] as const,
  manifest: (flightId: string) => ['crew', 'manifest', flightId] as const,
}
