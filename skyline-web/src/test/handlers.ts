import { http, HttpResponse, type JsonBodyType } from 'msw'

import type {
  AncillaryLine,
  AncillaryPurchaseRequest,
  GroupReservationRequest,
  SeatHoldRequest,
  CabinClass,
  FareType,
  RouteScope,
  SeatCategory,
  AircraftResponse,
  AircraftTypeResponse,
  AirportResponse,
  BoardingPassResponse,
  CrewMemberResponse,
  FlightCrewResponse,
  FlightResponse,
  NotificationResponse,
  ReservationResponse,
  RouteResponse,
  UserResponse,
  UserRole,
} from '@/api/types'
import { formatDateInZone, toApiDateTime } from '@/lib/format'
import { estimateOffers, hoursUntil, includedFor } from '@/lib/ancillaries'
import { estimatePrice, loadFactor, loadMultiplier } from '@/lib/pricing'
import { CATALOG } from './catalog'
import { expandLayout, LAYOUTS, SEAT_PRICES } from './seat-layouts'
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
  aircraftTypes: AircraftTypeResponse[]
  flights: FlightResponse[]
  reservations: ReservationResponse[]
  passes: Map<string, BoardingPassResponse>
  crew: CrewMemberResponse[]
  manifests: Map<string, FlightCrewResponse[]>
  /** flight id → seat → hold owner ("userId:slot"); holds never expire in the mock */
  holds: Map<string, Map<string, string>>
  /** In-app inbox entries, newest last; the mock writes them synchronously (the real service via Kafka). */
  notifications?: (NotificationResponse & { userId: string })[]
}

let counter = 0
const uuid = () => {
  counter += 1
  return `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`
}

function seedFlights(): FlightResponse[] {
  return [
    // On the two-cabin A321neo, so the booking flow can sell Business and draw a Business section.
    makeFlight({
      id: 'f0000000-0000-4000-8000-000000000001',
      flightNumber: 'SK101',
      departureTime: hoursFromNow(24),
      aircraft: seedAircraft[3]!,
      availableSeats: 160,
    }),
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
    aircraftTypes: structuredClone(aircraftTypes),
    flights: seedFlights(),
    reservations: [],
    passes: new Map(),
    crew: [],
    manifests: new Map(),
    holds: new Map(),
    notifications: [],
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

/** Adds an inbox entry, as the notification service would after a booking or a flight change. */
function notify(userId: string | null, kind: string, title: string, body: string, link: string | null) {
  if (!userId) return
  db.notifications ??= []
  db.notifications.push({ id: uuid(), userId, kind, title, body, link, createdAt: now(), read: false })
}

/** Tells each account with an active booking on the flight, once per account. */
function notifyFlight(flight: FlightResponse, kind: string, title: string, body: string) {
  const seen = new Set<string>()
  for (const reservation of db.reservations) {
    const userId = reservation.passenger.userId
    if (reservation.flight?.id !== flight.id || reservation.status === 'CANCELLED' || !userId || seen.has(userId))
      continue
    seen.add(userId)
    notify(userId, kind, title, body, `/trips/${reservation.id}`)
  }
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

function layoutOf(flight: FlightResponse) {
  const layout = LAYOUTS[flight.aircraft?.aircraftType.id ?? '']
  return layout ? expandLayout(layout) : null
}

function takenSeats(flightId: string) {
  return new Set(
    db.reservations
      .filter((r) => r.flight?.id === flightId && r.seatNumber && r.status !== 'CANCELLED')
      .map((r) => r.seatNumber!),
  )
}

/** A free seat at check-in: the first free ordinary seat in the cabin (the real service picks at random). */
function nextSeat(flight: FlightResponse, cabin: CabinClass) {
  const taken = takenSeats(flight.id)
  const held = db.holds.get(flight.id) ?? new Map<string, string>()
  const layout = layoutOf(flight)
  if (layout) {
    const free = layout.seats.filter((s) => s.cabin === cabin && !taken.has(s.code) && !held.has(s.code))
    const ordinary = free.filter((s) => s.category === 'STANDARD' || s.category === 'PREFERRED' || cabin !== 'ECONOMY')
    return (ordinary[0] ?? free[0])?.code ?? null
  }
  for (let index = 0; ; index += 1) {
    const seat = `${Math.floor(index / 6) + 1}${SEAT_LETTERS[index % 6]}`
    if (!taken.has(seat)) return seat
  }
}

function seatPrice(flight: FlightResponse, cabin: CabinClass, fare: FareType, category: SeatCategory) {
  const base = cabin === 'ECONOMY' ? SEAT_PRICES[fare]![category] : 0
  if (base === undefined) return null
  return base === 0 ? 0 : Math.round(base * loadMultiplier(loadFactor(flight)) * 100) / 100
}

function seatMapFor(flight: FlightResponse, cabin: CabinClass, fare: FareType, owner: string, booked: string | null) {
  const layout = layoutOf(flight)
  if (!layout) return null
  const taken = takenSeats(flight.id)
  const held = db.holds.get(flight.id) ?? new Map<string, string>()
  const seats = layout.seats.map((seat) => {
    const holder = held.get(seat.code)
    const mine = seat.code === booked || holder === owner
    const status = taken.has(seat.code) ? 'TAKEN' : holder ? 'HELD' : 'AVAILABLE'
    const price = seat.cabin === cabin ? seatPrice(flight, cabin, fare, seat.category) : null
    return {
      ...seat,
      status,
      price,
      selectable: price !== null && (status === 'AVAILABLE' || (status === 'HELD' && mine)),
      mine,
    } as const
  })
  const categories = [...new Set(layout.seats.filter((s) => s.cabin === cabin).map((s) => s.category))]
  return {
    flightId: flight.id,
    flightNumber: flight.flightNumber,
    aircraftTypeId: flight.aircraft!.aircraftType.id,
    aircraftName: `${flight.aircraft!.aircraftType.manufacturer} ${flight.aircraft!.aircraftType.model}`,
    body: layout.body,
    wing: layout.wing,
    sections: layout.sections,
    rows: layout.rows,
    facilities: layout.facilities,
    cabin,
    fareType: fare,
    currency: flight.currency,
    surchargePercent: Math.round((loadMultiplier(loadFactor(flight)) - 1) * 100),
    holdSeconds: 900,
    legend: categories
      .map((category) => ({
        category,
        price: seatPrice(flight, cabin, fare, category) ?? 0,
        available: seats.filter((s) => s.category === category && s.selectable).length,
      }))
      .sort((a, b) => a.price - b.price),
    seats,
  }
}

/** Holds a seat for an owner ("userId:slot"), releasing the owner's previous seat on that flight. */
function holdSeat(flight: FlightResponse, seat: string, owner: string) {
  const held = db.holds.get(flight.id) ?? new Map<string, string>()
  db.holds.set(flight.id, held)
  if (takenSeats(flight.id).has(seat) || (held.get(seat) && held.get(seat) !== owner)) return false
  for (const [code, holder] of held) if (holder === owner) held.delete(code)
  held.set(seat, owner)
  return true
}

function releaseHold(flightId: string, owner: string) {
  const held = db.holds.get(flightId)
  if (!held) return
  for (const [code, holder] of held) if (holder === owner) held.delete(code)
}

function recalcTotal(reservation: ReservationResponse) {
  // What was paid and kept: every line not refunded (as the reservation service computes it).
  const extras = (reservation.ancillaries ?? []).filter((l) => l.status !== 'REFUNDED').reduce((s, l) => s + l.price, 0)
  reservation.totalPrice = Math.round((reservation.farePrice + extras) * 100) / 100
}

function addLine(
  reservation: ReservationResponse,
  line: Omit<AncillaryLine, 'id' | 'createdAt' | 'channel' | 'status'>,
) {
  reservation.ancillaries = [
    ...(reservation.ancillaries ?? []),
    { ...line, id: uuid(), channel: 'ONLINE', status: 'ACTIVE', createdAt: now() },
  ]
  recalcTotal(reservation)
}

function endSeatLine(reservation: ReservationResponse) {
  for (const line of reservation.ancillaries ?? []) {
    if (line.type === 'SEAT' && line.status === 'ACTIVE') line.status = line.refundable ? 'REFUNDED' : 'CANCELLED'
  }
}

/** Like the schedule service: one flight at a time per aircraft, with 30 minutes on the ground in between. */
function aircraftConflict(
  plane: { id: string; tailNumber: string },
  excludeId: string | null,
  departure: string,
  arrival: string,
) {
  const turnaround = 30 * 60_000
  const from = Date.parse(`${departure}Z`) - turnaround
  const until = Date.parse(`${arrival}Z`) + turnaround
  const other = db.flights
    .filter((f) => f.aircraft?.id === plane.id && f.id !== excludeId)
    .filter((f) => f.status !== 'CANCELLED' && f.status !== 'ARRIVED')
    .filter((f) => Date.parse(`${f.departureTime}Z`) < until && Date.parse(`${f.arrivalTime}Z`) > from)
    .sort((a, b) => a.departureTime.localeCompare(b.departureTime))[0]
  return other
    ? fail(
        409,
        `Aircraft ${plane.tailNumber} already flies ${other.flightNumber} then (it needs 30 minutes on the ground between flights)`,
      )
    : null
}

function scopeOf(flight: FlightResponse): RouteScope {
  const route = flight.route
  return route && route.origin.country === route.destination.country ? 'DOMESTIC' : 'INTERNATIONAL'
}

function purchased(reservation: ReservationResponse) {
  const map: Record<string, number> = {}
  for (const line of reservation.ancillaries ?? []) {
    if (line.type !== 'SEAT' && line.status === 'ACTIVE')
      map[line.productCode] = (map[line.productCode] ?? 0) + line.quantity
  }
  return map
}

/** Offers for a booked passenger: the estimate rules, with what they already bought counted in. */
function optionsFor(reservation: ReservationResponse) {
  const flight = findFlight(reservation.flight!.id)!
  const bought = purchased(reservation)
  const offers = estimateOffers(
    CATALOG,
    reservation.cabinClass,
    reservation.fareType,
    scopeOf(flight),
    bought,
    hoursUntil(flight.departureTime),
  ).map((offer) => {
    const mine = bought[offer.code] ?? 0
    const more = Math.max(0, offer.maxAdditional - mine)
    return {
      ...offer,
      purchasedQuantity: mine,
      maxAdditional: more,
      available: offer.price !== null && more > 0,
      reason:
        offer.price !== null && more === 0 ? (offer.reason ?? 'You have the maximum for this flight') : offer.reason,
    }
  })
  return {
    reservationId: reservation.id,
    flightId: flight.id,
    routeScope: scopeOf(flight),
    channel: 'ONLINE' as const,
    currency: flight.currency,
    included: includedFor(CATALOG, reservation.cabinClass, reservation.fareType),
    offers,
  }
}

function newReservation(
  flight: FlightResponse,
  passenger: Omit<ReservationResponse['passenger'], 'id' | 'userId'>,
  userId: string,
  cabin: CabinClass,
  fare: FareType,
  groupId: string | null,
): ReservationResponse {
  const price = estimatePrice(flight, cabin, fare).amount
  return {
    id: uuid(),
    pnr: newPnr(),
    passenger: { ...passenger, id: uuid(), userId },
    flight,
    status: 'CONFIRMED',
    seatNumber: null,
    cabinClass: cabin,
    fareType: fare,
    farePrice: price,
    totalPrice: price,
    bookingGroupId: groupId,
    ancillaries: [],
    currency: flight.currency,
    bookedAt: now(),
    createdAt: now(),
    updatedAt: now(),
  }
}

function ownReservation(request: Request, id: string) {
  const { denied, user } = guard(request)
  if (denied) return { denied, reservation: null, user: null }
  const reservation = db.reservations.find((r) => r.id === id)
  if (!reservation || (user!.role === 'PASSENGER' && reservation.passenger.userId !== user!.id)) {
    return { denied: fail(404, 'Reservation not found'), reservation: null, user }
  }
  return { denied: null, reservation, user: user! }
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
    // Rotation, as the real API does: the old refresh token is revoked, a reuse gets a 401.
    db.refreshTokens.delete(body.refreshToken!)
    return ok(issueTokens(user))
  }),
  http.post(`${API}/auth/logout`, async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as { refreshToken?: string }
    db.refreshTokens.delete(body.refreshToken ?? '')
    return new HttpResponse(null, { status: 204 })
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
    const { cabinConfig: _config, ...type } = db.aircraftTypes.find((t) => t.id === body.aircraftTypeId)!
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
    const { denied, user } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as { status: AircraftResponse['status'] }
    const target = db.aircraft.find((a) => a.id === params.id)
    if (!target) return fail(404, 'Aircraft not found')
    // Like the fleet service: RETIRED is final, an admin's call, and refused while the aircraft still flies.
    if (target.status === 'RETIRED' && body.status !== 'RETIRED')
      return fail(409, 'A retired aircraft cannot return to service')
    if (body.status === 'RETIRED' && target.status !== 'RETIRED') {
      if (user!.role !== 'ADMIN') return fail(403, 'Only an admin can retire an aircraft')
      const flying = db.flights
        .filter((f) => f.aircraft?.id === target.id && f.status !== 'CANCELLED' && f.status !== 'ARRIVED')
        .sort((a, b) => a.departureTime.localeCompare(b.departureTime))
        .slice(0, 5)
        .map((f) => f.flightNumber)
      if (flying.length)
        return fail(
          409,
          `Aircraft ${target.tailNumber} still flies ${flying.join(', ')}: move them to another aircraft or cancel them first`,
        )
    }
    target.status = body.status
    return ok(target, 'Aircraft status updated')
  }),
  http.get(`${API}/aircraft-types`, ({ request }) => guard(request, ['STAFF', 'ADMIN']).denied ?? ok(db.aircraftTypes)),
  http.post(`${API}/aircraft-types`, async ({ request }) => {
    const { denied } = guard(request, ['ADMIN'])
    if (denied) return denied
    const body = (await request.json()) as Omit<AircraftTypeResponse, 'id'>
    const type: AircraftTypeResponse = { ...body, id: uuid(), cabinConfig: body.cabinConfig ?? null }
    db.aircraftTypes.push(type)
    return ok(type, 'Aircraft type created successfully', 201)
  }),

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
      // Like the server: `date` is a calendar day at the origin airport.
      .filter((f) => formatDateInZone(f.departureTime, f.route?.origin.timezone, 'yyyy-MM-dd') === date)
      .filter((f) => f.status === 'SCHEDULED' || f.status === 'DELAYED')
      // Like the server: sales close an hour before the scheduled departure.
      .filter((f) => hoursUntil(f.departureTime) >= 1)
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
    const busy = aircraftConflict(plane, null, String(body.departureTime), String(body.arrivalTime))
    if (busy) return busy
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
    if (typeof body.editVersion === 'number' && body.editVersion !== flight.editVersion)
      return fail(409, 'Someone else changed this flight since you opened it: reload it and make your change again')
    const plane = db.aircraft.find((a) => a.id === body.aircraftId)
    if (plane && plane.status !== 'ACTIVE') return fail(400, `Aircraft ${plane.tailNumber} is not active`)
    const busy = aircraftConflict(
      plane ?? flight.aircraft!,
      flight.id,
      String(body.departureTime),
      String(body.arrivalTime),
    )
    if (busy) return busy
    // New times are the plan now: like the server, a reschedule lifts the delay.
    const rescheduled = body.departureTime !== flight.departureTime || body.arrivalTime !== flight.arrivalTime
    Object.assign(flight, {
      flightNumber: String(body.flightNumber),
      route: db.routes.find((r) => r.id === body.routeId) ?? flight.route,
      aircraft: plane ?? flight.aircraft,
      departureTime: String(body.departureTime),
      arrivalTime: String(body.arrivalTime),
      basePrice: Number(body.basePrice),
      updatedAt: now(),
      editVersion: flight.editVersion + 1,
      ...(rescheduled && {
        status: flight.status === 'DELAYED' ? 'SCHEDULED' : flight.status,
        estimatedDepartureTime: null,
        estimatedArrivalTime: null,
      }),
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
    if (status === 'DELAYED' && flight.status !== 'DELAYED')
      return fail(400, `Delay a flight with its expected departure time (PUT /flights/${flight.id}/delay)`)
    const allowed: Record<string, string[]> = {
      SCHEDULED: ['BOARDING'],
      DELAYED: ['SCHEDULED', 'BOARDING'],
      BOARDING: ['DEPARTED'],
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
    if (flight.status === 'DELAYED' && status === 'SCHEDULED') {
      // Back on schedule lifts the delay, as on the server.
      flight.estimatedDepartureTime = null
      flight.estimatedArrivalTime = null
    }
    flight.status = status
    return ok(flight, 'Flight status updated successfully')
  }),
  // ---- notifications
  http.get(`${API}/notifications`, ({ request }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const mine = (db.notifications ?? [])
      .filter((n) => n.userId === user!.id)
      .reverse()
      .map(({ userId: _userId, ...n }) => n)
    return ok(page(mine, new URL(request.url)), 'Notifications retrieved')
  }),
  http.get(`${API}/notifications/unread-count`, ({ request }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    return ok({ count: (db.notifications ?? []).filter((n) => n.userId === user!.id && !n.read).length })
  }),
  http.post(`${API}/notifications/read-all`, ({ request }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    for (const n of db.notifications ?? []) if (n.userId === user!.id) n.read = true
    return ok({ count: 0 }, 'All notifications marked as read')
  }),
  http.post(`${API}/notifications/:id/read`, ({ request, params }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const entry = (db.notifications ?? []).find((n) => n.id === params.id && n.userId === user!.id)
    if (!entry) return fail(404, 'Notification not found')
    entry.read = true
    return ok(null, 'Notification marked as read')
  }),
  http.put(`${API}/flights/:id/delay`, async ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    if (!['SCHEDULED', 'DELAYED', 'BOARDING'].includes(flight.status))
      return fail(400, `Cannot delay a flight in status: ${flight.status}`)
    const { estimatedDepartureTime } = (await request.json()) as { estimatedDepartureTime: string }
    const delay = Date.parse(`${estimatedDepartureTime}Z`) - Date.parse(`${flight.departureTime}Z`)
    if (!(delay > 0)) return fail(400, 'Estimated departure must be after the scheduled departure')
    if (delay > 24 * 3_600_000)
      return fail(400, "A delay of more than 24 hours is a reschedule: change the flight's times instead")
    Object.assign(flight, {
      // Like the server: a boarding flight stays BOARDING, so sales don't reopen.
      status: flight.status === 'BOARDING' ? 'BOARDING' : 'DELAYED',
      estimatedDepartureTime,
      estimatedArrivalTime: toApiDateTime(new Date(Date.parse(`${flight.arrivalTime}Z`) + delay)),
      updatedAt: now(),
    })
    notifyFlight(flight, 'FLIGHT_DELAYED', `${flight.flightNumber} is delayed`, 'A new departure time is set.')
    return ok(flight, 'Flight delayed')
  }),
  http.delete(`${API}/flights/:id/delay`, ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    Object.assign(flight, {
      status: flight.status === 'DELAYED' ? 'SCHEDULED' : flight.status,
      estimatedDepartureTime: null,
      estimatedArrivalTime: null,
      updatedAt: now(),
    })
    return ok(flight, 'Flight delay lifted')
  }),
  http.put(`${API}/flights/:id/gate`, async ({ request, params }) => {
    const { denied } = guard(request, ['STAFF', 'ADMIN'])
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    if (['DEPARTED', 'ARRIVED', 'CANCELLED'].includes(flight.status))
      return fail(400, `Cannot change the gate of a flight in status: ${flight.status}`)
    const { gate } = (await request.json()) as { gate: string }
    if (!/^[A-Za-z]{0,2}[0-9]{1,3}[A-Za-z]?$/.test(gate.trim()))
      return fail(400, 'Validation failed', ['gate: Gate must look like B12, G5 or 214A'])
    Object.assign(flight, { gate: gate.trim().toUpperCase(), updatedAt: now() })
    notifyFlight(
      flight,
      'FLIGHT_GATE',
      `${flight.flightNumber}: gate ${flight.gate}`,
      `Departs from gate ${flight.gate}.`,
    )
    return ok(flight, 'Gate assigned')
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
    flight.availableSeats -= 1
    const reservation = newReservation(flight, body.passenger, user!.id, body.cabinClass, 'STANDARD', null)
    reservation.contactEmail = user!.email
    db.reservations.push(reservation)
    notify(
      user!.id,
      'BOOKING_CONFIRMED',
      `Booking confirmed: ${reservation.pnr}`,
      'Your trip is booked.',
      `/trips/${reservation.id}`,
    )
    return ok(withFlight(reservation), 'Flight booked successfully', 201)
  }),
  http.post(`${API}/reservations/group`, async ({ request }) => {
    const { denied, user } = guard(request, ['PASSENGER'])
    if (denied) return denied
    const body = (await request.json()) as GroupReservationRequest
    const errors = body.passengers.flatMap((p, i) =>
      validationErrors(p as unknown as Record<string, unknown>, [
        'firstName',
        'lastName',
        'passportNumber',
        'dateOfBirth',
        'nationality',
      ]).map((e) => `passengers[${i}].${e}`),
    )
    if (errors.length) return fail(400, 'Validation failed', errors)
    const groupId = uuid()
    const booked: ReservationResponse[] = []
    for (const leg of body.legs) {
      const flight = findFlight(leg.flightId)
      if (!flight) return fail(404, 'Flight not found')
      if (flight.availableSeats < body.passengers.length) return fail(400, 'Flight is fully booked')
      if (hoursUntil(flight.departureTime) < 1) return fail(409, 'Sales for this flight have closed')
      // Like the server: every extra-weight top-up needs a checked bag (included or in the basket), in any order.
      for (let index = 0; index < body.passengers.length; index++) {
        const chosen: Record<string, number> = {}
        for (const extra of body.extras ?? []) {
          if (extra.passengerIndex === index && extra.flightId === flight.id) chosen[extra.productCode] = extra.quantity
        }
        const offers = estimateOffers(CATALOG, leg.cabinClass, leg.fareType, scopeOf(flight), chosen)
        const count = (type: string) =>
          offers.filter((o) => o.type === type).reduce((sum, o) => sum + o.includedQuantity + (chosen[o.code] ?? 0), 0)
        if (count('EXTRA_WEIGHT') > count('CHECKED_BAG')) return fail(422, 'Extra weight: Add a checked bag first')
      }
      const layout = layoutOf(flight)
      if (layout && !layout.seats.some((s) => s.cabin === leg.cabinClass)) {
        return fail(
          422,
          `This aircraft has no ${leg.cabinClass.charAt(0)}${leg.cabinClass.slice(1).toLowerCase()} cabin`,
        )
      }
      for (const seat of body.seats ?? []) {
        if (seat.flightId !== leg.flightId) continue
        if (db.holds.get(flight.id)?.get(seat.seatNumber) !== `${user!.id}:p${seat.passengerIndex}`) {
          return fail(409, `Your hold on seat ${seat.seatNumber} has expired; please choose a seat again`)
        }
      }
      body.passengers.forEach((passenger, index) => {
        flight.availableSeats -= 1
        const reservation = newReservation(flight, passenger, user!.id, leg.cabinClass, leg.fareType, groupId)
        const seat = body.seats?.find((s) => s.passengerIndex === index && s.flightId === flight.id)
        if (seat && layout) {
          const category = layout.seats.find((s) => s.code === seat.seatNumber)!.category
          reservation.seatNumber = seat.seatNumber
          addLine(reservation, {
            type: 'SEAT',
            productCode: `SEAT_${category}`,
            description: `Seat ${seat.seatNumber}`,
            seatNumber: seat.seatNumber,
            quantity: 1,
            price: seatPrice(flight, leg.cabinClass, leg.fareType, category) ?? 0,
            refundable: leg.fareType === 'FLEX',
          })
          releaseHold(flight.id, `${user!.id}:p${index}`)
        }
        for (const extra of body.extras ?? []) {
          if (extra.passengerIndex !== index || extra.flightId !== flight.id) continue
          const product = CATALOG.find((p) => p.code === extra.productCode)!
          const unit = product.prices.find((p) => p.routeScope === scopeOf(flight))!.onlinePrice
          addLine(reservation, {
            type: product.type,
            productCode: product.code,
            description: product.name,
            seatNumber: null,
            quantity: extra.quantity,
            price: unit * extra.quantity,
            refundable: leg.fareType === 'FLEX' || product.alwaysRefundable,
          })
        }
        reservation.contactEmail = body.contactEmail?.trim() || user!.email
        db.reservations.push(reservation)
        booked.push(withFlight(reservation))
      })
    }
    notify(
      user!.id,
      'BOOKING_CONFIRMED',
      `Booking confirmed: ${[...new Set(booked.map((r) => r.pnr))].join(', ')}`,
      booked.length === 1 ? 'Your trip is booked.' : `${booked.length} reservations are booked.`,
      booked[0] ? `/trips/${booked[0].id}` : null,
    )
    const totalPrice = booked.reduce((sum, r) => sum + r.totalPrice, 0)
    return ok(
      { bookingGroupId: groupId, reservations: booked, totalPrice, currency: booked[0]!.currency },
      'Booking confirmed',
      201,
    )
  }),

  // ---- seats
  http.get(`${API}/flights/:id/seat-map`, ({ request, params }) => {
    const { denied, user } = guard(request)
    if (denied) return denied
    const flight = findFlight(String(params.id))
    if (!flight) return fail(404, 'Flight not found')
    const url = new URL(request.url)
    const reservationId = url.searchParams.get('reservationId')
    const reservation = reservationId ? db.reservations.find((r) => r.id === reservationId) : null
    const cabin = (reservation?.cabinClass ?? url.searchParams.get('cabin') ?? 'ECONOMY') as CabinClass
    const fare = (reservation?.fareType ?? url.searchParams.get('fareType') ?? 'STANDARD') as FareType
    const owner = reservation
      ? `${user!.id}:${reservation.id}`
      : `${user!.id}:p${url.searchParams.get('passenger') ?? 0}`
    const map = seatMapFor(flight, cabin, fare, owner, reservation?.seatNumber ?? null)
    return map ? ok(map) : fail(404, 'Seat selection is not available on this aircraft; a seat is assigned at check-in')
  }),
  http.post(`${API}/reservations/seat-holds`, async ({ request }) => {
    const { denied, user } = guard(request, ['PASSENGER'])
    if (denied) return denied
    const body = (await request.json()) as SeatHoldRequest
    const flight = findFlight(body.flightId)
    const seat = flight && layoutOf(flight)?.seats.find((s) => s.code === body.seatNumber)
    if (!flight || !seat) return fail(422, `Seat ${body.seatNumber} does not exist on this aircraft`)
    if (seat.cabin !== body.cabinClass) {
      return fail(422, `Seat ${seat.code} is in ${seat.cabin}; you can only choose a seat in ${body.cabinClass}`)
    }
    if (!holdSeat(flight, seat.code, `${user!.id}:p${body.passengerIndex}`)) {
      return fail(
        409,
        `Seat ${seat.code} was just taken by another passenger; the seat map has been updated, please choose another`,
      )
    }
    const price = seatPrice(flight, body.cabinClass, body.fareType, seat.category) ?? 0
    return ok(
      {
        flightId: flight.id,
        seatNumber: seat.code,
        category: seat.category,
        price,
        currency: flight.currency,
        expiresAt: hoursFromNow(0.25),
      },
      'Seat held',
      201,
    )
  }),
  http.delete(`${API}/reservations/seat-holds`, ({ request }) => {
    const { denied, user } = guard(request, ['PASSENGER'])
    if (denied) return denied
    const url = new URL(request.url)
    releaseHold(url.searchParams.get('flightId') ?? '', `${user!.id}:p${url.searchParams.get('passenger') ?? 0}`)
    return ok(null, 'Seat released')
  }),
  http.post(`${API}/reservations/:id/seat-hold`, async ({ request, params }) => {
    const { denied, reservation, user } = ownReservation(request, String(params.id))
    if (denied) return denied
    if (reservation.status === 'CHECKED_IN') {
      return fail(422, 'You are checked in: your seat is on your boarding pass and can’t be changed')
    }
    const { seatNumber } = (await request.json()) as { seatNumber: string }
    const flight = findFlight(reservation.flight!.id)!
    const seat = layoutOf(flight)?.seats.find((s) => s.code === seatNumber)
    if (!seat) return fail(422, `Seat ${seatNumber} does not exist on this aircraft`)
    if (seat.cabin !== reservation.cabinClass) {
      return fail(422, `Seat ${seat.code} is in ${seat.cabin}; you can only choose a seat in ${reservation.cabinClass}`)
    }
    if (!holdSeat(flight, seat.code, `${user.id}:${reservation.id}`)) {
      return fail(
        409,
        `Seat ${seat.code} was just taken by another passenger; the seat map has been updated, please choose another`,
      )
    }
    const price = seatPrice(flight, reservation.cabinClass, reservation.fareType, seat.category) ?? 0
    return ok(
      {
        flightId: flight.id,
        seatNumber: seat.code,
        category: seat.category,
        price,
        currency: flight.currency,
        expiresAt: hoursFromNow(0.25),
      },
      'Seat held',
      201,
    )
  }),
  http.delete(`${API}/reservations/:id/seat-hold`, ({ request, params }) => {
    const { denied, reservation, user } = ownReservation(request, String(params.id))
    if (denied) return denied
    releaseHold(reservation.flight!.id, `${user.id}:${reservation.id}`)
    return ok(null, 'Seat released')
  }),
  http.put(`${API}/reservations/:id/seat`, async ({ request, params }) => {
    const { denied, reservation, user } = ownReservation(request, String(params.id))
    if (denied) return denied
    const { seatNumber } = (await request.json()) as { seatNumber: string }
    const flight = findFlight(reservation.flight!.id)!
    if (db.holds.get(flight.id)?.get(seatNumber) !== `${user.id}:${reservation.id}`) {
      return fail(409, `Your hold on seat ${seatNumber} has expired; please choose a seat again`)
    }
    const category = layoutOf(flight)!.seats.find((s) => s.code === seatNumber)!.category
    endSeatLine(reservation)
    reservation.seatNumber = seatNumber
    addLine(reservation, {
      type: 'SEAT',
      productCode: `SEAT_${category}`,
      description: `Seat ${seatNumber}`,
      seatNumber,
      quantity: 1,
      price: seatPrice(flight, reservation.cabinClass, reservation.fareType, category) ?? 0,
      refundable: reservation.fareType === 'FLEX',
    })
    releaseHold(flight.id, `${user.id}:${reservation.id}`)
    return ok(withFlight(reservation), 'Seat confirmed')
  }),
  http.delete(`${API}/reservations/:id/seat`, ({ request, params }) => {
    const { denied, reservation } = ownReservation(request, String(params.id))
    if (denied) return denied
    endSeatLine(reservation)
    reservation.seatNumber = null
    recalcTotal(reservation)
    return ok(withFlight(reservation), 'Seat removed')
  }),

  // ---- add-ons
  http.get(`${API}/ancillary-products`, ({ request }) => guard(request).denied ?? ok(CATALOG)),
  http.get(`${API}/reservations/:id/ancillaries/options`, ({ request, params }) => {
    const { denied, reservation } = ownReservation(request, String(params.id))
    if (denied) return denied
    return ok(optionsFor(reservation))
  }),
  http.post(`${API}/reservations/:id/ancillaries`, async ({ request, params }) => {
    const { denied, reservation } = ownReservation(request, String(params.id))
    if (denied) return denied
    const body = (await request.json()) as AncillaryPurchaseRequest
    for (const item of body.items) {
      const offer = optionsFor(reservation).offers.find((o) => o.code === item.productCode)
      if (!offer || !offer.available || item.quantity > offer.maxAdditional) {
        return fail(422, `${offer?.name ?? item.productCode}: ${offer?.reason ?? 'not available'}`)
      }
      const product = CATALOG.find((p) => p.code === item.productCode)!
      addLine(reservation, {
        type: product.type,
        productCode: product.code,
        description: product.name,
        seatNumber: null,
        quantity: item.quantity,
        price: (offer.price ?? 0) * item.quantity,
        refundable: reservation.fareType === 'FLEX' || product.alwaysRefundable,
      })
    }
    return ok(withFlight(reservation), 'Add-ons added')
  }),
  http.delete(`${API}/reservations/:id/ancillaries/:lineId`, ({ request, params }) => {
    const { denied, reservation } = ownReservation(request, String(params.id))
    if (denied) return denied
    const line = reservation.ancillaries?.find((l) => l.id === params.lineId)
    if (!line || line.status !== 'ACTIVE') return fail(404, 'Add-on not found on this reservation')
    line.status = line.refundable ? 'REFUNDED' : 'CANCELLED'
    recalcTotal(reservation)
    return ok(withFlight(reservation), 'Add-on removed')
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
    for (const line of reservation.ancillaries ?? []) {
      if (line.status === 'ACTIVE') line.status = line.refundable ? 'REFUNDED' : 'CANCELLED'
    }
    recalcTotal(reservation)
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
    const expected = new Date(`${flight.estimatedDepartureTime ?? flight.departureTime}Z`).getTime()
    if (Date.now() < departure - 48 * 3_600_000) return fail(422, 'Check-in opens 48 hours before departure.')
    if (Date.now() > departure - 3_600_000) return fail(422, 'Check-in closes 1 hour before departure.')
    reservation.seatNumber ??= nextSeat(flight, reservation.cabinClass)
    if (!reservation.seatNumber) return fail(409, 'No seats left in your cabin on this flight')
    reservation.status = 'CHECKED_IN'
    const pass: BoardingPassResponse = {
      pnr,
      passengerName: `${reservation.passenger.firstName} ${reservation.passenger.lastName}`,
      flightNumber: flight.flightNumber,
      origin: flight.route?.origin.iataCode ?? null,
      destination: flight.route?.destination.iataCode ?? null,
      departureTime: flight.departureTime,
      estimatedDepartureTime: flight.estimatedDepartureTime,
      boardingTime: toApiDateTime(new Date(expected - 45 * 60_000)),
      gate: flight.gate ?? 'G7',
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

// ------------------------------------------------------------------ persistence (browser mode)

interface SerializedDb extends Omit<Db, 'tokens' | 'refreshTokens' | 'passes' | 'manifests' | 'holds'> {
  tokens: [string, string][]
  refreshTokens: [string, string][]
  passes: [string, BoardingPassResponse][]
  manifests: [string, FlightCrewResponse[]][]
  holds?: [string, [string, string][]][]
  counter: number
}

/** Snapshot of the mock back-end, so a full page reload in the browser keeps its data. */
export function exportDb(): string {
  const snapshot: SerializedDb = {
    ...db,
    tokens: [...db.tokens],
    refreshTokens: [...db.refreshTokens],
    passes: [...db.passes],
    manifests: [...db.manifests],
    holds: [...db.holds].map(([flightId, seats]) => [flightId, [...seats]]),
    counter,
  }
  return JSON.stringify(snapshot)
}

export function importDb(json: string) {
  const snapshot = JSON.parse(json) as SerializedDb
  counter = snapshot.counter
  db = {
    ...snapshot,
    tokens: new Map(snapshot.tokens),
    refreshTokens: new Map(snapshot.refreshTokens),
    passes: new Map(snapshot.passes),
    manifests: new Map(snapshot.manifests),
    holds: new Map((snapshot.holds ?? []).map(([flightId, seats]) => [flightId, new Map(seats)])),
  }
}
