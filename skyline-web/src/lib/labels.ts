import type { AircraftStatus, CabinClass, CrewRole, FlightStatus, ReservationStatus, UserRole } from '@/api/types'

/** Friendly names for enums: sentence case, never the raw constant. */
export const flightStatusLabel: Record<FlightStatus, string> = {
  SCHEDULED: 'Scheduled',
  BOARDING: 'Boarding',
  DEPARTED: 'Departed',
  ARRIVED: 'Arrived',
  CANCELLED: 'Cancelled',
  DELAYED: 'Delayed',
}

export const reservationStatusLabel: Record<ReservationStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  CHECKED_IN: 'Checked in',
}

export const cabinLabel: Record<CabinClass, string> = {
  ECONOMY: 'Economy',
  BUSINESS: 'Business',
  FIRST: 'First',
}

export const crewRoleLabel: Record<CrewRole, string> = {
  CAPTAIN: 'Captain',
  FIRST_OFFICER: 'First officer',
  PURSER: 'Purser',
  FLIGHT_ATTENDANT: 'Flight attendant',
}

export const aircraftStatusLabel: Record<AircraftStatus, string> = {
  ACTIVE: 'Active',
  MAINTENANCE: 'Maintenance',
  RETIRED: 'Retired',
}

export const userRoleLabel: Record<UserRole, string> = {
  PASSENGER: 'Passenger',
  STAFF: 'Staff',
  ADMIN: 'Admin',
}

export const CABINS: readonly CabinClass[] = ['ECONOMY', 'BUSINESS', 'FIRST']
export const CREW_ROLES: readonly CrewRole[] = ['CAPTAIN', 'FIRST_OFFICER', 'PURSER', 'FLIGHT_ATTENDANT']
export const FLIGHT_STATUSES: readonly FlightStatus[] = [
  'SCHEDULED',
  'DELAYED',
  'BOARDING',
  'DEPARTED',
  'ARRIVED',
  'CANCELLED',
]
export const AIRCRAFT_STATUSES: readonly AircraftStatus[] = ['ACTIVE', 'MAINTENANCE', 'RETIRED']
