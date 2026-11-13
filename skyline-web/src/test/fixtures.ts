import type {
  AircraftResponse,
  AircraftTypeResponse,
  AirportResponse,
  BoardingPassResponse,
  FlightResponse,
  ReservationResponse,
  RouteResponse,
} from '@/api/types'
import { toApiDateTime } from '@/lib/format'

/** Test data for MSW (tests and the mocked Playwright suite only; never shipped). */

export const airports: AirportResponse[] = [
  {
    id: '11111111-1111-1111-1111-111111111101',
    iataCode: 'IST',
    icaoCode: 'LTFM',
    name: 'Istanbul Airport',
    city: 'Istanbul',
    country: 'Turkey',
    timezone: 'Europe/Istanbul',
  },
  {
    id: '11111111-1111-1111-1111-111111111102',
    iataCode: 'LHR',
    icaoCode: 'EGLL',
    name: 'Heathrow Airport',
    city: 'London',
    country: 'United Kingdom',
    timezone: 'Europe/London',
  },
  {
    id: '11111111-1111-1111-1111-111111111103',
    iataCode: 'JFK',
    icaoCode: 'KJFK',
    name: 'John F. Kennedy International Airport',
    city: 'New York',
    country: 'United States',
    timezone: 'America/New_York',
  },
]

const summary = (airport: AirportResponse) => {
  const { icaoCode: _icao, ...rest } = airport
  return rest
}

export const routes: RouteResponse[] = [
  {
    id: 'c0000000-0000-4000-8000-000000000001',
    origin: summary(airports[0]!),
    destination: summary(airports[1]!),
    distanceKm: 2500,
    estimatedDurationMinutes: 240,
  },
  {
    id: 'c0000000-0000-4000-8000-000000000002',
    origin: summary(airports[1]!),
    destination: summary(airports[2]!),
    distanceKm: 5540,
    estimatedDurationMinutes: 475,
  },
]

export const aircraftTypes: AircraftTypeResponse[] = [
  {
    id: '22222222-2222-2222-2222-222222222201',
    manufacturer: 'Airbus',
    model: 'A320neo',
    totalSeats: 180,
    cabinConfig: 'Y180',
  },
  {
    id: '22222222-2222-2222-2222-222222222202',
    manufacturer: 'Boeing',
    model: '737-800',
    totalSeats: 189,
    cabinConfig: null,
  },
  {
    id: '22222222-2222-2222-2222-222222222203',
    manufacturer: 'Boeing',
    model: '787-9',
    totalSeats: 296,
    cabinConfig: 'J30 Y266',
  },
]

const typeSummary = (type: AircraftTypeResponse) => {
  const { cabinConfig: _config, ...rest } = type
  return rest
}

export const aircraft: AircraftResponse[] = [
  {
    id: 'a0000000-0000-4000-8000-000000000001',
    tailNumber: 'TC-SKA',
    aircraftType: typeSummary(aircraftTypes[0]!),
    status: 'ACTIVE',
    manufacturedYear: 2021,
  },
  {
    id: 'a0000000-0000-4000-8000-000000000002',
    tailNumber: 'TC-SKB',
    aircraftType: typeSummary(aircraftTypes[2]!),
    status: 'ACTIVE',
    manufacturedYear: 2019,
  },
  {
    id: 'a0000000-0000-4000-8000-000000000003',
    tailNumber: 'TC-SKM',
    aircraftType: typeSummary(aircraftTypes[1]!),
    status: 'MAINTENANCE',
    manufacturedYear: 2012,
  },
]

const HOUR = 3_600_000

/** A UTC hour offset from now, truncated to the minute. */
export function hoursFromNow(hours: number, now = Date.now()) {
  const date = new Date(now + hours * HOUR)
  date.setUTCSeconds(0, 0)
  return toApiDateTime(date)
}

export function makeFlight(overrides: Partial<FlightResponse> = {}): FlightResponse {
  const departureTime = overrides.departureTime ?? hoursFromNow(24)
  const route = overrides.route === undefined ? routes[0]! : overrides.route
  const minutes = route?.estimatedDurationMinutes ?? 240
  const arrival = new Date(new Date(`${departureTime}Z`).getTime() + minutes * 60_000)
  return {
    id: 'f0000000-0000-4000-8000-000000000001',
    flightNumber: 'SK101',
    route,
    aircraft: aircraft[0]!,
    departureTime,
    arrivalTime: toApiDateTime(arrival),
    status: 'SCHEDULED',
    availableSeats: 150,
    basePrice: 120,
    currency: 'EUR',
    createdAt: hoursFromNow(-48),
    updatedAt: hoursFromNow(-48),
    ...overrides,
  }
}

export function makeReservation(overrides: Partial<ReservationResponse> = {}): ReservationResponse {
  return {
    id: 'b0000000-0000-4000-8000-000000000001',
    pnr: 'K7Q2MX',
    passenger: {
      id: 'd0000000-0000-4000-8000-000000000001',
      userId: 'e0000000-0000-4000-8000-000000000001',
      firstName: 'Ada',
      lastName: 'Lovelace',
      passportNumber: 'U12345678',
      dateOfBirth: '1990-05-14',
      nationality: 'United Kingdom',
    },
    flight: makeFlight(),
    status: 'CONFIRMED',
    seatNumber: null,
    cabinClass: 'ECONOMY',
    totalPrice: 120,
    currency: 'EUR',
    bookedAt: hoursFromNow(-2),
    createdAt: hoursFromNow(-2),
    updatedAt: hoursFromNow(-2),
    ...overrides,
  }
}

export function makeBoardingPass(overrides: Partial<BoardingPassResponse> = {}): BoardingPassResponse {
  const departureTime = overrides.departureTime ?? hoursFromNow(24)
  return {
    pnr: 'K7Q2MX',
    passengerName: 'Ada Lovelace',
    flightNumber: 'SK101',
    origin: 'IST',
    destination: 'LHR',
    departureTime,
    boardingTime: toApiDateTime(new Date(new Date(`${departureTime}Z`).getTime() - 45 * 60_000)),
    gate: 'G7',
    seatNumber: '1A',
    barcodeData: 'SKY-K7Q2MX-3F9A1C2B',
    issuedAt: hoursFromNow(0),
    ...overrides,
  }
}

function base64Url(value: string) {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** An unsigned JWT with Skyline's claim shape (the client never verifies signatures). */
export function fakeJwt(claims: {
  sub: string
  userId: string
  roles: string[]
  type?: 'access' | 'refresh'
  expInSeconds?: number
}) {
  const now = Math.floor(Date.now() / 1000)
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = base64Url(
    JSON.stringify({
      sub: claims.sub,
      userId: claims.userId,
      roles: claims.roles,
      type: claims.type ?? 'access',
      iat: now,
      exp: now + (claims.expInSeconds ?? 900),
    }),
  )
  return `${header}.${payload}.signature`
}
