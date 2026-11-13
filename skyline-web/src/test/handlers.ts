import { http, HttpResponse, type JsonBodyType } from 'msw'

import type {
  AircraftResponse,
  AirportResponse,
  BoardingPassResponse,
  CrewMemberResponse,
  FlightCrewResponse,
  FlightResponse,
  ReservationResponse,
  RouteResponse,
  UserResponse,
  UserRole,
} from '@/api/types'
import { toApiDateTime } from '@/lib/format'
import { CABIN_MULTIPLIER, estimatePrice } from '@/lib/pricing'
import {
  aircraft as seedAircraft,
  aircraftTypes,
  airports as seedAirports,
  fakeJwt,
  hoursFromNow,
  makeFlight,
  routes as seedRoutes,
} from './fixtures'

/**
 * An in-memory Skyline back-end for MSW. Used by unit/integration tests and by the default (mocked)
 * Playwright suite; it is never part of the shipped app. It follows the real API's envelope, status codes
 * and messages closely enough to exercise every UI state.
 */

const API = '*/api/v1'

interface MockUser extends UserResponse {
  password: string
}

interface Db {
  users: MockUser[]
  /** access token → user id */
  tokens: Map<string, string>
  refreshTokens: Map<string, string>
  airports: AirportResponse[]
  routes: RouteResponse[]
  aircraft: AircraftResponse[]
  flights: FlightResponse[]
  reservations: ReservationResponse[]
  passes: Map<string, BoardingPassResponse>
  crew: CrewMemberResponse[]
  manifests: Map<string, FlightCrewResponse[]>
}

let counter = 0
const uuid = () => {
  counter += 1
  return `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`
}

function seedFlights(): FlightResponse[] {
  return [
    makeFlight({ id: 'f0000000-0000-4000-8000-000000000001', flightNumber: 'SK101', departureTime: hoursFromNow(24) }),
    makeFlight({
      id: 'f0000000-0000-4000-8000-000000000002',
      flightNumber: 'SK103',
      departureTime: hoursFromNow(27),
      status: 'DELAYED',
      availableSeats: 12,
    }),
    makeFlight({
      id: 'f0000000-0000-4000-8000-000000000003',
      flightNumber: 'SK201',
      departureTime: hoursFromNow(96),
      route: seedRoutes[1]!,
      aircraft: seedAircraft[1]!,
    }),
  ]
}

function freshDb(): Db {
  return {
    users: [
      {
        id: 'e0000000-0000-4000-8000-000000000001',
        email: 'ada@skyline.test',
        role: 'PASSENGER',
        password: 'Passw0rd!!',
      },
      {
        id: 'e0000000-0000-4000-8000-000000000002',
        email: 'staff@skyline.test',
        role: 'STAFF',
        password: 'Staff-Passw0rd!',
      },
      {
        id: 'e0000000-0000-4000-8000-000000000003',
        email: 'admin@skyline.test',
        role: 'ADMIN',
        password: 'Admin-Passw0rd!',
      },
    ],
    tokens: new Map(),
    refreshTokens: new Map(),
    airports: structuredClone(seedAirports),
    routes: structuredClone(seedRoutes),
    aircraft: structuredClone(seedAircraft),
    flights: seedFlights(),
    reservations: [],
    passes: new Map(),
    crew: [],
    manifests: new Map(),
  }
}

export let db: Db = freshDb()

/** Back to the seed data (between tests). */
export function resetDb() {
  db = freshDb()
}

// ------------------------------------------------------------------ envelope helpers

const now = () => toApiDateTime(new Date())

export function ok<T extends JsonBodyType>(data: T, message: string | null = null, status = 200) {
  return HttpResponse.json({ success: true, message, data, timestamp: now(), errors: null }, { status })
}

export function fail(status: number, message: string, errors: string[] | null = null) {
  return HttpResponse.json({ success: false, message, data: null, timestamp: now(), errors }, { status })
}

function page<T>(items: T[], url: URL, defaultSize = 20) {
  const number = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? defaultSize)
  const content = items.slice(number * size, number * size + size)
  const totalPages = Math.ceil(items.length / size)
  return {
    content,
    totalElements: items.length,
    totalPages,
    number,
    size,
    first: number === 0,
    last: number >= totalPages - 1,
    empty: content.length === 0,
    numberOfElements: content.length,
  }
}

function caller(request: Request): MockUser | null {
  const header = request.headers.get('Authorization')
  if (!header?.startsWith('Bearer ')) return null
  const userId = db.tokens.get(header.slice(7))
  return db.users.find((user) => user.id === userId) ?? null
}

/** 401 without a valid token, 403 without the role; null when allowed. */
function guard(request: Request, roles?: UserRole[]) {
  const user = caller(request)
  if (!user) return { denied: fail(401, 'Authentication required'), user: null }
  if (roles && !roles.includes(user.role)) return { denied: fail(403, 'Access denied'), user }
  return { denied: null, user }
}

function issueTokens(user: MockUser) {
  const accessToken = fakeJwt({ sub: user.email, userId: user.id, roles: [`ROLE_${user.role}`] })
  const refreshToken =
    fakeJwt({ sub: user.email, userId: user.id, roles: [`ROLE_${user.role}`], type: 'refresh', expInSeconds: 604800 }) +
    `.${uuid()}`
  db.tokens.set(accessToken, user.id)
  db.refreshTokens.set(refreshToken, user.id)
  return { accessToken, refreshToken, tokenType: 'Bearer', expiresIn: 900 }
}

const publicUser = ({ password: _password, ...user }: MockUser): UserResponse => user

function findFlight(id: string) {
  return db.flights.find((flight) => flight.id === id)
}

/** Reservation with the flight as it is now (the API enriches on every read). */
function withFlight(reservation: ReservationResponse): ReservationResponse {
  return { ...reservation, flight: findFlight(reservation.flight?.id ?? '') ?? reservation.flight }
}

function redacted(reservation: ReservationResponse): ReservationResponse {
  const passport = reservation.passenger.passportNumber
  return {
    ...reservation,
    passenger: {
      ...reservation.passenger,
      userId: null,
      dateOfBirth: null,
      passportNumber: '*'.repeat(Math.max(0, passport.length - 3)) + passport.slice(-3),
    },
  }
}

const PNR_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
function newPnr() {
  let pnr = ''
  for (let i = 0; i < 6; i += 1) pnr += PNR_ALPHABET[Math.floor(Math.random() * PNR_ALPHABET.length)]
  return pnr
}

const SEAT_LETTERS = 'ABCDEF'
function nextSeat(flightId: string) {
  const taken = new Set(
    db.reservations.filter((r) => r.flight?.id === flightId && r.seatNumber).map((r) => r.seatNumber),
  )
  for (let index = 0; ; index += 1) {
    const seat = `${Math.floor(index / 6) + 1}${SEAT_LETTERS[index % 6]}`
    if (!taken.has(seat)) return seat
  }
}

function validationErrors(body: Record<string, unknown>, required: string[]) {
  const errors = required.filter((field) => {
    const value = field
      .split('.')
      .reduce<unknown>((acc, key) => (acc as Record<string, unknown> | undefined)?.[key], body)
    return value === undefined || value === null || value === ''
  })
  return errors.map((field) => `${field}: must not be blank`)
}

// ------------------------------------------------------------------ handlers

export const handlers = [
  // ---- auth
  http.post(`${API}/auth/register`, async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string }
    const email = (body.email ?? '').trim().toLowerCase()
    if (!email.includes('@') || (body.password ?? '').length < 8) {
      return fail(400, 'Validation failed', [
        ...(!email.includes('@') ? ['email: must be a well-formed email address'] : []),
        ...((body.password ?? '').length < 8 ? ['password: size must be between 8 and 128'] : []),
      ])
    }
    if (db.users.some((user) => user.email === email)) return fail(409, 'Email is already registered')
    const user: MockUser = { id: uuid(), email, role: 'PASSENGER', password: body.password! }
    db.users.push(user)
    return ok(publicUser(user), 'Registration successful', 201)
  }),
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string }
    const user = db.users.find((u) => u.email === (body.email ?? '').trim().toLowerCase())
    if (!user || user.password !== body.password) return fail(401, 'Invalid email or password')
    return ok(issueTokens(user))
  }),
  http.post(`${API}/auth/refresh`, async ({ request }) => {
    const body = (await request.json()) as { refreshToken?: string }
    const userId = db.refreshTokens.get(body.refreshToken ?? '')
    const user = db.users.find((u) => u.id === userId)
    if (!user) return fail(401, 'Invalid refresh token')
    return ok(issueTokens(user))
  }),
  http.get(`${API}/auth/me`, ({ request }) => {
    const { denied, user } = guard(request)
    return denied ?? ok(publicUser(user!))
  }),

  // ---- users (ADMIN)
  http.get(`${API}/users`, ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const url = new URL(request.url)
    const email = url.searchParams.get('email')?.toLowerCase()
    const role = url.searchParams.get('role')
    const users = db.users
      .filter((u) => (!email || u.email.includes(email)) && (!role || u.role === role))
      .map(publicUser)
    return ok(page(users, url))
  }),
  http.post(`${API}/users`, async ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as { email: string; password: string; role: UserRole }
    if ((body.password ?? '').length < 12)
      return fail(400, 'Validation failed', ['password: size must be between 12 and 128'])
    const email = body.email.trim().toLowerCase()
    if (db.users.some((u) => u.email === email)) return fail(409, 'Email is already registered')
    const user: MockUser = { id: uuid(), email, role: body.role, password: body.password }
    db.users.push(user)
    return ok(publicUser(user), 'User created successfully', 201)
  }),

  // ---- network
  http.get(`${API}/airports`, () => ok(db.airports)),
  http.post(`${API}/airports`, async ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as Omit<AirportResponse, 'id'>
    if (db.airports.some((a) => a.iataCode === body.iataCode)) return fail(409, 'Request conflicts with existing data')
    const airport = { ...body, id: uuid() }
    db.airports.push(airport)
    return ok(airport, 'Airport created successfully', 201)
  }),
  http.get(`${API}/routes/search`, ({ request }) => {
    const url = new URL(request.url)
    const origin = url.searchParams.get('originIata')
    const destination = url.searchParams.get('destinationIata')
    return ok(db.routes.filter((r) => r.origin.iataCode === origin && r.destination.iataCode === destination))
  }),
  http.get(`${API}/routes`, () => ok(db.routes)),
  http.post(`${API}/routes`, async ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as {
      originAirportId: string
      destinationAirportId: string
      distanceKm: number
      estimatedDurationMinutes: number
    }
    if (body.originAirportId === body.destinationAirportId) return fail(400, 'Origin and destination must differ')
    if (db.routes.some((r) => r.origin.id === body.originAirportId && r.destination.id === body.destinationAirportId)) {
      return fail(409, 'Route already exists')
    }
    const toSummary = (id: string) => {
      const { icaoCode: _icao, ...summary } = db.airports.find((a) => a.id === id)!
      return summary
    }
    const route: RouteResponse = {
      id: uuid(),
      origin: toSummary(body.originAirportId),
      destination: toSummary(body.destinationAirportId),
      distanceKm: body.distanceKm,
      estimatedDurationMinutes: body.estimatedDurationMinutes,
    }
    db.routes.push(route)
    return ok(route, 'Route created successfully', 201)
  }),

  // ---- fleet
  http.get(`${API}/aircraft`, ({ request }) => guard(request, ['STAFF', 'ADMIN']).denied ?? ok(db.aircraft)),
  http.post(`${API}/aircraft`, async ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as { tailNumber: string; aircraftTypeId: string; manufacturedYear: number }
    const tail = body.tailNumber.toUpperCase()
    if (db.aircraft.some((a) => a.tailNumber === tail))
      return fail(409, 'Aircraft with this tail number already exists')
    const { cabinConfig: _config, ...type } = aircraftTypes.find((t) => t.id === body.aircraftTypeId)!
    const created: AircraftResponse = {
      id: uuid(),
      tailNumber: tail,
      aircraftType: type,
      status: 'ACTIVE',
      manufacturedYear: body.manufacturedYear,
    }
    db.aircraft.push(created)
    return ok(created, 'Aircraft created successfully', 201)
  }),
  http.patch(`${API}/aircraft/:id/status`, async ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as { status: AircraftResponse['status'] }
    const target = db.aircraft.find((a) => a.id === params.id)
    if (!target) return fail(404, 'Aircraft not found')
    target.status = body.status
    return ok(target, 'Aircraft status updated')
  }),
  http.get(`${API}/aircraft-types`, ({ request }) => guard(request, ['STAFF', 'ADMIN']).denied ?? ok(aircraftTypes)),

  // ---- flights
  http.get(`${API}/flights/board`, ({ request }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const url = new URL(request.url)
    const date = url.searchParams.get('date')
    const statuses = url.searchParams.getAll('status')
    const number = url.searchParams.get('flightNumber')?.toLowerCase()
    const routeId = url.searchParams.get('routeId')
    const flights = db.flights
      .filter((f) => (!date || f.departureTime.startsWith(date)) && (!statuses.length || statuses.includes(f.status)))
      .filter(
        (f) => (!number || f.flightNumber.toLowerCase().includes(number)) && (!routeId || f.route?.id === routeId),
      )
      .sort((a, b) => a.departureTime.localeCompare(b.departureTime))
    return ok(page(flights, url, 50))
  }),
  http.get(`${API}/flights/:id/crew`, ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    return denied ?? ok(db.manifests.get(String(params.id)) ?? [])
  }),
  http.post(`${API}/flights/:id/crew`, async ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as { crewMemberId: string; role: FlightCrewResponse['assignedRole'] }
    const member = db.crew.find((c) => c.id === body.crewMemberId)
    const flight = findFlight(String(params.id))
    if (!member || !flight) return fail(404, 'Crew member not found')
    if (member.licenseExpiryDate < flight.departureTime.slice(0, 10)) {
      return fail(400, 'Crew member license is expired or will expire before flight')
    }
    const manifest = db.manifests.get(flight.id) ?? []
    if (manifest.some((m) => m.crewMemberId === member.id))
      return fail(409, 'Crew member is already assigned to an overlapping flight')
    const assignment: FlightCrewResponse = {
      crewMemberId: member.id,
      employeeNumber: member.employeeNumber,
      firstName: member.firstName,
      lastName: member.lastName,
      assignedRole: body.role,
    }
    db.manifests.set(flight.id, [...manifest, assignment])
    return ok(assignment, 'Crew assigned to flight successfully')
  }),
  http.delete(`${API}/flights/:flightId/crew/:crewMemberId`, ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const manifest = db.manifests.get(String(params.flightId)) ?? []
    db.manifests.set(
      String(params.flightId),
      manifest.filter((m) => m.crewMemberId !== params.crewMemberId),
    )
    return ok(null, 'Crew member removed from flight successfully')
  }),
  http.get(`${API}/flights/:id`, ({ params }) => {
    const flight = findFlight(String(params.id))
    return flight ? ok(flight) : fail(404, `Flight not found with id: ${String(params.id)}`)
  }),
  http.get(`${API}/flights`, ({ request }) => {
    const url = new URL(request.url)
    const origin = url.searchParams.get('origin')
    const destination = url.searchParams.get('destination')
    const date = url.searchParams.get('date')
    if (!origin || !destination || !date)
      return fail(400, `Missing required parameter '${!origin ? 'origin' : !destination ? 'destination' : 'date'}'`)
    const sort = url.searchParams.get('sort') ?? 'departureTime,asc'
    const flights = db.flights
      .filter((f) => f.route?.origin.iataCode === origin && f.route.destination.iataCode === destination)
      .filter((f) => f.departureTime.startsWith(date) && (f.status === 'SCHEDULED' || f.status === 'DELAYED'))
      .sort((a, b) => (sort.endsWith('desc') ? -1 : 1) * a.departureTime.localeCompare(b.departureTime))
    return ok(page(flights, url))
  }),
  http.post(`${API}/flights`, async ({ request }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as Record<string, unknown>
    const errors = validationErrors(body, [
      'flightNumber',
      'routeId',
      'aircraftId',
      'departureTime',
      'arrivalTime',
      'basePrice',
    ])
    if (errors.length) return fail(400, 'Validation failed', errors)
    const plane = db.aircraft.find((a) => a.id === body.aircraftId)
    if (!plane) return fail(404, 'Aircraft not found')
    if (plane.status !== 'ACTIVE') return fail(400, `Aircraft ${plane.tailNumber} is not active`)
    const flight = makeFlight({
      id: uuid(),
      flightNumber: String(body.flightNumber).toUpperCase(),
      route: db.routes.find((r) => r.id === body.routeId) ?? null,
      aircraft: plane,
      departureTime: String(body.departureTime),
      arrivalTime: String(body.arrivalTime),
      basePrice: Number(body.basePrice),
      availableSeats: plane.aircraftType.totalSeats,
      status: 'SCHEDULED',
    })
    db.flights.push(flight)
    return ok(flight, 'Flight created successfully', 201)
  }),
  http.put(`${API}/flights/:id`, async ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    if (flight.status !== 'SCHEDULED' && flight.status !== 'DELAYED')
      return fail(400, `Cannot update flight in status: ${flight.status}`)
    const body = (await request.json()) as Record<string, unknown>
    const plane = db.aircraft.find((a) => a.id === body.aircraftId)
    if (plane && plane.status !== 'ACTIVE') return fail(400, `Aircraft ${plane.tailNumber} is not active`)
    Object.assign(flight, {
      flightNumber: String(body.flightNumber),
      route: db.routes.find((r) => r.id === body.routeId) ?? flight.route,
      aircraft: plane ?? flight.aircraft,
      departureTime: String(body.departureTime),
      arrivalTime: String(body.arrivalTime),
      basePrice: Number(body.basePrice),
      updatedAt: now(),
    })
    return ok(flight, 'Flight updated successfully')
  }),
  http.patch(`${API}/flights/:id/status`, async ({ request, params }) => {
    const { denied, user } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    const { status } = (await request.json()) as { status: FlightResponse['status'] }
    if (status === 'CANCELLED') {
      if (user!.role !== 'ADMIN') return fail(403, 'Only an admin can cancel a flight')
      cancelFlight(flight)
      return ok(flight, 'Flight status updated successfully')
    }
    const allowed: Record<string, string[]> = {
      SCHEDULED: ['BOARDING', 'DELAYED'],
      DELAYED: ['SCHEDULED', 'BOARDING'],
      BOARDING: ['DEPARTED', 'DELAYED'],
      DEPARTED: ['ARRIVED'],
    }
    if (!allowed[flight.status]?.includes(status))
      return fail(400, `Invalid status transition from ${flight.status} to ${status}`)
    if (status === 'BOARDING' || status === 'DEPARTED') {
      const manifest = db.manifests.get(flight.id) ?? []
      if (
        !manifest.some((m) => m.assignedRole === 'CAPTAIN') ||
        !manifest.some((m) => m.assignedRole === 'FIRST_OFFICER')
      ) {
        return fail(400, 'Flight must have at least one CAPTAIN and one FIRST_OFFICER to proceed')
      }
    }
    flight.status = status
    return ok(flight, 'Flight status updated successfully')
  }),
  http.delete(`${API}/flights/:id`, ({ request, params }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    if (['CANCELLED', 'DEPARTED', 'ARRIVED'].includes(flight.status))
      return fail(400, `Cannot cancel flight in status: ${flight.status}`)
    cancelFlight(flight)
    return ok(null, 'Flight cancelled successfully')
  }),

  // ---- reservations
  http.post(`${API}/reservations`, async ({ request }) => {
    const { denied, user } = guard(request, ['PASSENGER'])
    if (denied) return denied
    const body = (await request.json()) as {
      flightId: string
      cabinClass: ReservationResponse['cabinClass']
      passenger: Omit<ReservationResponse['passenger'], 'id' | 'userId'>
    }
    const errors = validationErrors(body as unknown as Record<string, unknown>, [
      'passenger.firstName',
      'passenger.lastName',
      'passenger.passportNumber',
      'passenger.dateOfBirth',
      'passenger.nationality',
    ])
    if (errors.length) return fail(400, 'Validation failed', errors)
    const flight = findFlight(body.flightId)
    if (!flight) return fail(404, 'Flight not found')
    if (flight.status !== 'SCHEDULED' && flight.status !== 'DELAYED')
      return fail(400, 'Cannot book a flight that is not scheduled')
    if (flight.availableSeats <= 0) return fail(400, 'Flight is fully booked')
    const price = estimatePrice(flight, body.cabinClass).amount
    flight.availableSeats -= 1
    const reservation: ReservationResponse = {
      id: uuid(),
      pnr: newPnr(),
      passenger: { ...body.passenger, id: uuid(), userId: user!.id },
      flight,
      status: 'CONFIRMED',
      seatNumber: null,
      cabinClass: body.cabinClass,
      totalPrice: price || flight.basePrice * CABIN_MULTIPLIER[body.cabinClass],
      currency: flight.currency,
      bookedAt: now(),
      createdAt: now(),
      updatedAt: now(),
    }
    db.reservations.push(reservation)
    return ok(withFlight(reservation), 'Flight booked successfully', 201)
  }),
  http.get(`${API}/reservations/my`, ({ request }) => {
    const { denied, user } = guard(request, ['PASSENGER'])
    if (denied) return denied
    const mine = db.reservations
      .filter((r) => r.passenger.userId === user!.id)
      .sort((a, b) => b.bookedAt.localeCompare(a.bookedAt))
      .map(withFlight)
    return ok(page(mine, new URL(request.url)))
  }),
  http.get(`${API}/reservations/pnr/:pnr`, ({ request, params }) => {
    const reservation = db.reservations.find((r) => r.pnr === String(params.pnr).toUpperCase())
    const user = caller(request)
    const privileged = user && (user.role !== 'PASSENGER' || reservation?.passenger.userId === user.id)
    if (reservation && privileged) return ok(withFlight(reservation))
    const lastName = new URL(request.url).searchParams.get('lastName')
    if (!lastName) return fail(400, "Missing required parameter 'lastName'")
    if (!reservation || reservation.passenger.lastName.toLowerCase() !== lastName.toLowerCase()) {
      return fail(404, 'Reservation not found')
    }
    return ok(redacted(withFlight(reservation)))
  }),
  http.get(`${API}/reservations/:id`, ({ request, params }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const reservation = db.reservations.find((r) => r.id === params.id)
    if (!reservation || (user!.role === 'PASSENGER' && reservation.passenger.userId !== user!.id)) {
      return fail(404, 'Reservation not found')
    }
    return ok(withFlight(reservation))
  }),
  http.delete(`${API}/reservations/:id`, ({ request, params }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const reservation = db.reservations.find((r) => r.id === params.id)
    if (!reservation || (user!.role === 'PASSENGER' && reservation.passenger.userId !== user!.id)) {
      return fail(404, 'Reservation not found')
    }
    if (reservation.status === 'CANCELLED') return fail(400, 'Reservation is already cancelled')
    const flight = findFlight(reservation.flight?.id ?? '')
    if (flight && flight.status !== 'SCHEDULED' && flight.status !== 'DELAYED')
      return fail(400, 'Cannot cancel a reservation for this flight')
    reservation.status = 'CANCELLED'
    reservation.seatNumber = null
    if (flight) flight.availableSeats += 1
    return ok(null, 'Reservation cancelled successfully')
  }),

  // ---- check-in
  http.post(`${API}/checkin`, async ({ request }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const { pnr } = (await request.json()) as { pnr: string }
    const reservation = db.reservations.find((r) => r.pnr === pnr)
    if (!reservation || (user!.role === 'PASSENGER' && reservation.passenger.userId !== user!.id)) {
      return fail(404, 'Reservation not found')
    }
    const existing = db.passes.get(reservation.id)
    if (existing) return ok(existing, 'Check-in successful')
    if (reservation.status !== 'CONFIRMED') {
      return fail(422, `Check-in is only allowed for CONFIRMED reservations. Current status: ${reservation.status}`)
    }
    const flight = findFlight(reservation.flight!.id)!
    const departure = new Date(`${flight.departureTime}Z`).getTime()
    if (Date.now() < departure - 48 * 3_600_000) return fail(422, 'Check-in opens 48 hours before departure.')
    if (Date.now() > departure - 3_600_000) return fail(422, 'Check-in closes 1 hour before departure.')
    reservation.seatNumber = nextSeat(flight.id)
    reservation.status = 'CHECKED_IN'
    const pass: BoardingPassResponse = {
      pnr,
      passengerName: `${reservation.passenger.firstName} ${reservation.passenger.lastName}`,
      flightNumber: flight.flightNumber,
      origin: flight.route?.origin.iataCode ?? null,
      destination: flight.route?.destination.iataCode ?? null,
      departureTime: flight.departureTime,
      boardingTime: toApiDateTime(new Date(departure - 45 * 60_000)),
      gate: 'G7',
      seatNumber: reservation.seatNumber,
      barcodeData: `SKY-${pnr}-3F9A1C2B`,
      issuedAt: now(),
    }
    db.passes.set(reservation.id, pass)
    return ok(pass, 'Check-in successful')
  }),
  http.get(`${API}/checkin/:pnr/boarding-pass`, ({ request, params }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const reservation = db.reservations.find((r) => r.pnr === params.pnr)
    if (!reservation || (user!.role === 'PASSENGER' && reservation.passenger.userId !== user!.id)) {
      return fail(404, 'Reservation not found')
    }
    const pass = db.passes.get(reservation.id)
    return pass ? ok(pass) : fail(404, 'Boarding pass not found')
  }),

  // ---- crew
  http.get(`${API}/crew/:id`, ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const member = db.crew.find((c) => c.id === params.id)
    return member ? ok(member) : fail(404, 'Crew member not found')
  }),
  http.get(`${API}/crew`, ({ request }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const url = new URL(request.url)
    const role = url.searchParams.get('role')
    const q = url.searchParams.get('q')?.toLowerCase()
    const list = db.crew
      .filter(
        (c) =>
          (!role || c.role === role) &&
          (!q || `${c.firstName} ${c.lastName} ${c.employeeNumber}`.toLowerCase().includes(q)),
      )
      .sort((a, b) => a.lastName.localeCompare(b.lastName))
    return ok(list)
  }),
  http.post(`${API}/crew`, async ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as Omit<CrewMemberResponse, 'id'>
    if (db.crew.some((c) => c.employeeNumber.toLowerCase() === body.employeeNumber.toLowerCase())) {
      return fail(409, 'Employee number already exists')
    }
    const member = { ...body, id: uuid() }
    db.crew.push(member)
    return ok(member, 'Crew member added successfully')
  }),
]

function cancelFlight(flight: FlightResponse) {
  flight.status = 'CANCELLED'
  flight.availableSeats = flight.aircraft?.aircraftType.totalSeats ?? flight.availableSeats
  // The real system does this asynchronously over Kafka.
  db.reservations
    .filter((r) => r.flight?.id === flight.id)
    .forEach((r) => {
      r.status = 'CANCELLED'
      r.seatNumber = null
    })
}

/** Logs a mock user in and stores the tokens, for tests that start authenticated. */
export function issueSessionFor(email: string) {
  const user = db.users.find((u) => u.email === email)
  if (!user) throw new Error(`No mock user ${email}`)
  return issueTokens(user)
}
