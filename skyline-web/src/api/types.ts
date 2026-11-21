/**
 * DTOs of the Skyline API (gateway /api/v1). Every LocalDateTime is a UTC wall-clock time without an
 * offset, e.g. "2026-12-01T14:30:00"; parse it with parseUtc() from lib/format.
 */
export type UserRole = 'PASSENGER' | 'STAFF' | 'ADMIN'
export type FlightStatus = 'SCHEDULED' | 'BOARDING' | 'DEPARTED' | 'ARRIVED' | 'CANCELLED' | 'DELAYED'
export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'CHECKED_IN'
export type CabinClass = 'ECONOMY' | 'BUSINESS' | 'FIRST'
export type AircraftStatus = 'ACTIVE' | 'MAINTENANCE' | 'RETIRED'
export type CrewRole = 'CAPTAIN' | 'FIRST_OFFICER' | 'PURSER' | 'FLIGHT_ATTENDANT'
export type UUID = string
export type LocalDateTime = string
export type LocalDate = string

export interface ApiEnvelope<T> {
  success: boolean
  message: string | null
  data: T | null
  timestamp: string
  errors: string[] | null
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  /** 0-based */
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
  numberOfElements: number
}

export interface UserResponse {
  id: UUID
  email: string
  role: UserRole
}
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  /** Seconds. */
  expiresIn: number
}

export interface AirportResponse {
  id: UUID
  iataCode: string
  icaoCode: string
  name: string
  city: string
  country: string
  timezone: string
}
export interface AirportSummary {
  id: UUID
  iataCode: string
  name: string
  city: string
  country: string
  timezone: string
}
export interface RouteResponse {
  id: UUID
  origin: AirportSummary
  destination: AirportSummary
  distanceKm: number
  estimatedDurationMinutes: number
}

export interface AircraftTypeResponse {
  id: UUID
  manufacturer: string
  model: string
  totalSeats: number
  cabinConfig: string | null
}
export interface AircraftTypeSummary {
  id: UUID
  manufacturer: string
  model: string
  totalSeats: number
}
export interface AircraftResponse {
  id: UUID
  tailNumber: string
  aircraftType: AircraftTypeSummary
  status: AircraftStatus
  manufacturedYear: number
}

export interface FlightResponse {
  id: UUID
  flightNumber: string
  /** Null if the route service was unreachable when enriching. */
  route: RouteResponse | null
  /** Null likewise; render a graceful fallback. */
  aircraft: AircraftResponse | null
  departureTime: LocalDateTime
  arrivalTime: LocalDateTime
  status: FlightStatus
  availableSeats: number
  basePrice: number
  /** ISO 4217 */
  currency: string
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}

export interface PassengerResponse {
  id: UUID
  userId: UUID | null
  firstName: string
  lastName: string
  /** Masked ("*****123") in the public redacted view. */
  passportNumber: string
  /** Null in the redacted view. */
  dateOfBirth: LocalDate | null
  nationality: string
}
export interface ReservationResponse {
  id: UUID
  pnr: string
  passenger: PassengerResponse
  flight: FlightResponse | null
  status: ReservationStatus
  /** Null until chosen, or until check-in assigns one. */
  seatNumber: string | null
  cabinClass: CabinClass
  fareType: FareType
  /** The ticket alone. */
  farePrice: number
  /** Fare plus paid add-ons (seat, bags, ...). */
  totalPrice: number
  /** Shared by reservations booked together; null for a single booking. */
  bookingGroupId: UUID | null
  /** Paid extras, every status, oldest first. */
  ancillaries: AncillaryLine[] | null
  /** ISO 4217 */
  currency: string
  bookedAt: LocalDateTime
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}

export interface BoardingPassResponse {
  pnr: string
  passengerName: string
  flightNumber: string
  /** IATA codes */
  origin: string | null
  destination: string | null
  departureTime: LocalDateTime
  /** Departure − 45 min. */
  boardingTime: LocalDateTime
  gate: string
  seatNumber: string
  /** "SKY-<PNR>-<8 hex>", encoded into the PDF417. */
  barcodeData: string
  issuedAt: LocalDateTime
}

// ------------------------------------------------------------------ seats

export type FareType = 'BASIC' | 'STANDARD' | 'FLEX'
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'TAKEN' | 'BLOCKED'
export type SeatPosition = 'WINDOW' | 'MIDDLE' | 'AISLE'
export type SeatCategory = 'EXIT_ROW' | 'EXTRA_LEGROOM' | 'FRONT' | 'PREFERRED' | 'STANDARD'
export type SeatFeature =
  'EXIT_ROW' | 'BULKHEAD' | 'NEAR_LAVATORY' | 'NEAR_GALLEY' | 'LIMITED_RECLINE' | 'ACCESSIBLE' | 'OVER_WING'
export type FacilityType = 'LAVATORY' | 'GALLEY' | 'CLOSET'
export type FacilitySide = 'LEFT' | 'CENTER' | 'RIGHT' | 'FULL'

export interface SeatView {
  /** e.g. "12A" */
  code: string
  row: number
  letter: string
  cabin: CabinClass
  position: SeatPosition
  legroom: 'STANDARD' | 'EXTRA'
  features: SeatFeature[]
  category: SeatCategory
  status: SeatStatus
  /** This passenger's price; null outside their cabin. */
  price: number | null
  selectable: boolean
  /** Held or booked by this passenger. */
  mine: boolean
}
export interface SeatMapResponse {
  flightId: UUID
  flightNumber: string
  aircraftTypeId: UUID
  aircraftName: string
  body: 'NARROW' | 'WIDE'
  wing: { fromRow: number; toRow: number } | null
  /** Cabins front to back, seat letters grouped by aisle. */
  sections: { cabin: CabinClass; fromRow: number; toRow: number; columnGroups: string[][] }[]
  rows: { number: number; cabin: CabinClass; exit: boolean; overWing: boolean }[]
  /** afterRow 0 = in front of the first row. */
  facilities: { type: FacilityType; afterRow: number; side: FacilitySide }[]
  cabin: CabinClass
  fareType: FareType
  currency: string
  /** Nearly-full surcharge included in paid seat prices (0, 15 or 30). */
  surchargePercent: number
  holdSeconds: number
  legend: { category: SeatCategory; price: number; available: number }[]
  seats: SeatView[]
}
export interface SeatHoldResponse {
  flightId: UUID
  seatNumber: string
  category: SeatCategory
  price: number
  currency: string
  expiresAt: LocalDateTime
}

// ------------------------------------------------------------------ add-ons

export type AncillaryType =
  | 'SEAT'
  | 'CABIN_BAG'
  | 'CHECKED_BAG'
  | 'EXTRA_WEIGHT'
  | 'SPORTS_EQUIPMENT'
  | 'PRIORITY_BOARDING'
  | 'MEAL'
  | 'LEGROOM_BUNDLE'
export type SalesChannel = 'ONLINE' | 'AIRPORT'
export type RouteScope = 'DOMESTIC' | 'INTERNATIONAL'

export interface AncillaryLine {
  id: UUID
  type: AncillaryType
  productCode: string
  description: string
  seatNumber: string | null
  quantity: number
  price: number
  channel: SalesChannel
  status: 'ACTIVE' | 'CANCELLED' | 'REFUNDED'
  refundable: boolean
  createdAt: LocalDateTime
}
export interface AncillaryOffer {
  code: string
  type: AncillaryType
  name: string
  description: string
  weightKg: number | null
  includedQuantity: number
  purchasedQuantity: number
  maxAdditional: number
  /** Unit price on this channel. */
  price: number | null
  onlinePrice: number | null
  airportPrice: number | null
  available: boolean
  reason: string | null
}
export interface AncillaryOptionsResponse {
  reservationId: UUID
  flightId: UUID
  routeScope: RouteScope
  channel: SalesChannel
  currency: string
  included: { code: string; name: string; quantity: number }[]
  offers: AncillaryOffer[]
}
export interface AncillaryProductResponse {
  code: string
  type: AncillaryType
  name: string
  description: string
  weightKg: number | null
  maxQuantity: number
  cutoffHours: number
  alwaysRefundable: boolean
  active: boolean
  prices: { routeScope: RouteScope; onlinePrice: number; airportPrice: number }[]
  includedIn: { cabinClass: CabinClass; fareType: FareType; quantity: number }[]
  currency: string
}

export interface CrewMemberResponse {
  id: UUID
  userId: UUID
  employeeNumber: string
  firstName: string
  lastName: string
  role: CrewRole
  licenseNumber: string
  licenseExpiryDate: LocalDate
}
export interface FlightCrewResponse {
  crewMemberId: UUID
  employeeNumber: string
  firstName: string
  lastName: string
  assignedRole: CrewRole
}

// ------------------------------------------------------------------ requests

export interface RegisterRequest {
  email: string
  password: string
}
export interface LoginRequest {
  email: string
  password: string
}
export interface PassengerRequest {
  firstName: string
  lastName: string
  passportNumber: string
  dateOfBirth: LocalDate
  nationality: string
}
export interface ReservationRequest {
  flightId: UUID
  passenger: PassengerRequest
  cabinClass: CabinClass
  fareType?: FareType
}
/** Several passengers on one or more legs, all or nothing; seats must be held first. */
export interface GroupReservationRequest {
  passengers: PassengerRequest[]
  legs: { flightId: UUID; cabinClass: CabinClass; fareType: FareType }[]
  seats?: { passengerIndex: number; flightId: UUID; seatNumber: string }[]
  extras?: { passengerIndex: number; flightId: UUID; productCode: string; quantity: number }[]
}
export interface GroupReservationResponse {
  bookingGroupId: UUID
  reservations: ReservationResponse[]
  totalPrice: number
  currency: string
}
export interface SeatHoldRequest {
  flightId: UUID
  seatNumber: string
  passengerIndex: number
  cabinClass: CabinClass
  fareType: FareType
}
export interface AncillaryPurchaseRequest {
  items: { productCode: string; quantity: number }[]
  channel?: SalesChannel
}
export interface CheckInRequest {
  pnr: string
}
export interface FlightRequest {
  flightNumber: string
  routeId: UUID
  aircraftId: UUID
  /** UTC, "yyyy-MM-dd'T'HH:mm:ss", no offset. */
  departureTime: LocalDateTime
  arrivalTime: LocalDateTime
  basePrice: number
}
export interface UpdateFlightStatusRequest {
  status: FlightStatus
}
export interface AircraftRequest {
  tailNumber: string
  aircraftTypeId: UUID
  manufacturedYear: number
}
export interface AircraftTypeRequest {
  manufacturer: string
  model: string
  totalSeats: number
  cabinConfig?: string
}
export interface UpdateAircraftStatusRequest {
  status: AircraftStatus
}
export interface AirportRequest {
  iataCode: string
  icaoCode: string
  name: string
  city: string
  country: string
  timezone: string
}
export interface RouteRequest {
  originAirportId: UUID
  destinationAirportId: UUID
  distanceKm: number
  estimatedDurationMinutes: number
}
export interface CrewMemberRequest {
  userId: UUID
  employeeNumber: string
  firstName: string
  lastName: string
  role: CrewRole
  licenseNumber: string
  licenseExpiryDate: LocalDate
}
export interface FlightCrewAssignmentRequest {
  crewMemberId: UUID
  role: CrewRole
}
export interface CreateUserRequest {
  email: string
  password: string
  role: 'STAFF' | 'ADMIN'
}
export interface FlightBoardParams {
  date?: string
  status?: FlightStatus[]
  flightNumber?: string
  routeId?: UUID
  page?: number
  size?: number
  sort?: string
}
export interface PageParams {
  page?: number
  size?: number
  sort?: string
}
