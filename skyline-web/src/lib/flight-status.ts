import type { FlightCrewResponse, FlightResponse, FlightStatus } from '@/api/types'

/**
 * The flight status machine, mirrored for guidance (the server decides). CANCELLED is deliberately absent:
 * it goes through DELETE /flights/{id}, offered to ADMIN only.
 */
const NEXT: Record<FlightStatus, readonly FlightStatus[]> = {
  SCHEDULED: ['BOARDING', 'DELAYED'],
  DELAYED: ['SCHEDULED', 'BOARDING'],
  BOARDING: ['DEPARTED', 'DELAYED'],
  DEPARTED: ['ARRIVED'],
  ARRIVED: [],
  CANCELLED: [],
}

export function nextStatuses(current: FlightStatus): readonly FlightStatus[] {
  return NEXT[current]
}

/** BOARDING and DEPARTED need at least one captain and one first officer. */
export function requiresCrew(status: FlightStatus) {
  return status === 'BOARDING' || status === 'DEPARTED'
}

export function isTerminal(status: FlightStatus) {
  return status === 'ARRIVED' || status === 'CANCELLED'
}

/** Cancellation (ADMIN): not from CANCELLED, DEPARTED or ARRIVED. */
export function isCancellableFlight(status: FlightStatus) {
  return status === 'SCHEDULED' || status === 'DELAYED' || status === 'BOARDING'
}

/** Edits are allowed while SCHEDULED or DELAYED. */
export function isEditableFlight(status: FlightStatus) {
  return status === 'SCHEDULED' || status === 'DELAYED'
}

/** Passengers can book SCHEDULED and DELAYED flights. */
export function isBookable(flight: Pick<FlightResponse, 'status' | 'availableSeats'>) {
  return (flight.status === 'SCHEDULED' || flight.status === 'DELAYED') && flight.availableSeats > 0
}

export interface CrewReadiness {
  captain: boolean
  firstOfficer: boolean
  ready: boolean
}

export function crewReadiness(manifest: readonly Pick<FlightCrewResponse, 'assignedRole'>[]): CrewReadiness {
  const captain = manifest.some((member) => member.assignedRole === 'CAPTAIN')
  const firstOfficer = manifest.some((member) => member.assignedRole === 'FIRST_OFFICER')
  return { captain, firstOfficer, ready: captain && firstOfficer }
}

/** The main line of the status stepper; DELAYED and CANCELLED are branches off it. */
export const MAIN_LINE: readonly FlightStatus[] = ['SCHEDULED', 'BOARDING', 'DEPARTED', 'ARRIVED']

/** Verb-first button copy for each target status. */
export const transitionCopy: Record<FlightStatus, string> = {
  SCHEDULED: 'Back to scheduled',
  DELAYED: 'Mark as delayed',
  BOARDING: 'Move to boarding',
  DEPARTED: 'Mark as departed',
  ARRIVED: 'Mark as arrived',
  CANCELLED: 'Cancel flight',
}
