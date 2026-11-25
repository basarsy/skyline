<div align="center">

```
███████╗██╗  ██╗██╗   ██╗██╗     ██╗███╗   ██╗███████╗
██╔════╝██║ ██╔╝╚██╗ ██╔╝██║     ██║████╗  ██║██╔════╝
███████╗█████╔╝  ╚████╔╝ ██║     ██║██╔██╗ ██║█████╗  
╚════██║██╔═██╗   ╚██╔╝  ██║     ██║██║╚██╗██║██╔══╝  
███████║██║  ██╗   ██║   ███████╗██║██║ ╚████║███████╗
╚══════╝╚═╝  ╚═╝   ╚═╝   ╚══════╝╚═╝╚═╝  ╚═══╝╚══════╝
```

**Airline Management System**

*A full-stack airline platform: Spring Boot microservices behind a gateway, and a React web app for passengers and operations staff*

---

![Java](https://img.shields.io/badge/Java-21-000000?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-000000?style=flat-square&logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.0-000000?style=flat-square&logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-000000?style=flat-square&logo=postgresql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Kafka-7.6-000000?style=flat-square&logo=apachekafka&logoColor=white)
![React](https://img.shields.io/badge/React-19-000000?style=flat-square&logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-6-000000?style=flat-square&logo=typescript&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4-000000?style=flat-square&logo=tailwindcss&logoColor=white)
![Status](https://img.shields.io/badge/Status-In_Development-555555?style=flat-square)

<br />

<img src=".github/screenshots/01-home.png" alt="The Skyline home page: a photo of a plane wing over clouds, a tabbed booking box, and destination cards with fares" width="100%" />

</div>

---

## Overview

Skyline is a full-stack airline management system: a set of Spring Boot services behind an API gateway, and a React web app on top. It covers the full lifecycle of airline operations — from aircraft fleet management and route scheduling to passenger reservations, check-in, crew assignments, and passenger notifications (email and in-app) about bookings, delays and gate changes.

The project is built as a **microservices architecture**: each bounded context runs as an independent Spring Boot application with its own database, communicating over HTTP (OpenFeign) and asynchronous events (Kafka). A Spring Cloud Gateway provides a unified API entry point.

**[skyline-web](skyline-web/README.md)** is the browser front-end on top of it: a React 19 + TypeScript single-page app with two faces. Passengers search flights, book, manage their trips, check in and get a boarding pass with a scannable PDF417 barcode. Operations staff run the flight board, walk flights through their status lifecycle, assign crew, and manage the fleet, network and user accounts. It talks to the gateway only, through the same `/api/v1` the services expose, and ships as an nginx container in the same `docker compose` stack.

---

## Walkthrough

Every screenshot below comes from the real app, running against its mocked API so the data is the same every time. One command regenerates them: see [Regenerating the screenshots](#regenerating-the-screenshots).

### Passenger journey

**1. Find a flight.** The home page (above) leads with a tabbed booking box: search flights, look up a booking by reference and last name, or check in. "Where we fly" lists every airport the route service knows, with the lowest estimated Economy fare over the next 7 days, found by searching each direct route day by day.

**2. Compare flights.** Results come from the schedule service (`GET /flights?origin=&destination=&date=`) and include delayed flights. Departures show in the origin airport's time zone and arrivals in the destination's, with `+1` when the local date changes. Prices are estimates that mirror the reservation service's rules, including the surcharge on nearly full flights. Hovering a nearby date prefetches its results.

<img src=".github/screenshots/02-results.png" alt="Search results for Istanbul to London: a date strip and two flight cards with times, status and estimated prices" width="100%" />

**3. Book in five steps.** Cabin and fare, passengers, seats, extras, review. The cabin step previews the fare for each cabin the aircraft has (Economy 1×, Business 2.5×, First 4× the base fare) and each fare (Basic, Standard, Flex). Up to nine passengers book together; the form validates as you go and links every error to its field.

<table>
  <tr>
    <td width="50%"><img src=".github/screenshots/03-booking-cabin.png" alt="Choose your cabin and fare: the cabins this aircraft has and Basic, Standard and Flex fares, with prices and the estimated total" /></td>
    <td width="50%"><img src=".github/screenshots/04-booking-review.png" alt="Review your booking: flight times, passenger details, cabin and fare, the chosen seat and extras" /></td>
  </tr>
</table>

**Seats.** A top-down seat map drawn from the aircraft's layout data (one generic component for every aircraft): fuselage, wings, exits, lavatories and galleys, cabin dividers, row numbers and aisles. Seats show their price category by colour and glyph, and their state (yours, a companion's, taken, being chosen by someone else) by icon and pattern, never by colour alone. Hover, focus or tap shows the seat's details; arrow keys move between seats. A chosen seat is held for 15 minutes; if someone else got it first, the map refreshes. Skipping is fine: check-in assigns a free seat next to the rest of the booking. The same component draws a two-cabin narrow-body (A321neo, left) and a 3-3-3 wide-body (787-9, right):

<table>
  <tr>
    <td width="50%"><img src=".github/screenshots/14-seat-map.png" alt="A321neo seat map for a Business passenger: nose, galley and lavatory, Business 2-2 with seat 2A chosen, Economy 3-3 greyed out as another cabin, exit rows and wings, and a seat summary" /></td>
    <td width="50%"><img src=".github/screenshots/16-seat-map-wide.png" alt="787-9 seat map for an Economy passenger: Business 2-2-2 greyed out, Economy 3-3-3 coloured by price category with exit, front-of-cabin and window or aisle glyphs, two exit doors, and a details card for seat 12D at 12 euros" /></td>
  </tr>
</table>

<img src=".github/screenshots/17-mobile-seat-map.png" alt="The 787-9 seat map on a phone, scrollable and zoomable, with the price and Skip seats button above the tab bar" width="32%" />

**Extras.** Bags (cabin bag, checked bags, extra weight, sports equipment), priority boarding and meals, per passenger, with what the fare already includes shown first and the airport price for comparison.

<img src=".github/screenshots/15-extras.png" alt="Bags and extras for a Business passenger: what the fare includes (cabin bag, two 30 kg bags, priority boarding, hot meal), bags with online and airport prices, and meals closed 24 hours before departure" width="100%" />

**4. Confirmed.** `POST /reservations/group` (one reservation per passenger, sharing a booking group) holds a seat in the flight's inventory (the schedule service, over an internal service-key endpoint), computes the price on the server, and issues a six-character booking reference (PNR). The confirmation page shows the server's total, offers an `.ics` calendar file, and tells you when check-in opens.

<img src=".github/screenshots/05-confirmation.png" alt="Booking confirmation: the booking reference, flight times, total price, and a check-in button" width="100%" />

**5. My trips.** Upcoming, past and cancelled bookings. "Seat and bags" changes the seat or adds bags until check-in. Cancelling a booking frees its seat on the flight.

<img src=".github/screenshots/06-trips.png" alt="My trips: an upcoming Istanbul to London booking with Check in and Cancel booking buttons" width="100%" />

**6. Check in and board.** Check-in opens 48 hours before departure and closes 1 hour before. Before checking in, a passenger can still choose (and pay for) a seat or add bags. `POST /checkin` keeps the chosen seat or assigns a free one, marks the reservation `CHECKED_IN`, and returns the boarding pass. It's idempotent: checking in again returns the same pass. The pass carries a PDF417 barcode, prints cleanly, and downloads as an image.

<img src=".github/screenshots/18-checkin-seat.png" alt="Check-in seat choice on the A321neo: exit-row seat 12A held for 25 euros with its details card, the price legend and key, and a Pay 25 euros for 12A button" width="100%" />

<table>
  <tr>
    <td width="68%"><img src=".github/screenshots/07-boarding-pass.png" alt="Boarding pass for Ada Lovelace, IST to LHR, gate G7, seat 2A, with a PDF417 barcode" /></td>
    <td width="32%"><img src=".github/screenshots/12-mobile-boarding-pass.png" alt="The same boarding pass on a phone" /></td>
  </tr>
</table>

### Operations console

Staff and admins get a separate console at `/ops`, gated by role: a passenger who opens it sees an access-denied page.

**7. Flight board.** Every flight on every status, filterable by date, route, status and flight number (`GET /flights/board`), with seats sold and base fares.

<img src=".github/screenshots/08-ops-board.png" alt="Operations flight board: counts per status, and a table of flights with route, times, aircraft, status, seats left and base price" width="100%" />

**8. Run a flight.** The status machine (`SCHEDULED → BOARDING → DEPARTED → ARRIVED`, with `DELAYED` and `CANCELLED` branches) offers only the moves allowed from the current state. Boarding is refused until a captain and a first officer are assigned (the schedule service checks with the crew service). Cancelling is admin-only, and every booking on the flight is cancelled through a Kafka event.

<img src=".github/screenshots/09-ops-flight.png" alt="Flight SK101 in the ops console: status steps, a crew manifest missing its captain and first officer, seat inventory, and a cancel section" width="100%" />

**9. Fleet, network, crew and users.** Aircraft and aircraft types (only `ACTIVE` aircraft can be scheduled), airports and routes, crew members with licence expiry dates, and staff accounts that an admin creates.

<img src=".github/screenshots/10-ops-fleet.png" alt="Fleet page: four aircraft with type, seats, year and status" width="100%" />

### Dark mode and phones

The theme follows the system, or the account menu's System / Light / Dark switch. Every screen is laid out for phones down to 375px wide, with a bottom tab bar.

<table>
  <tr>
    <td width="70%"><img src=".github/screenshots/11-home-dark.png" alt="The home page in dark mode" /></td>
    <td width="30%"><img src=".github/screenshots/13-mobile-home.png" alt="The home page on a phone" /></td>
  </tr>
</table>

### Regenerating the screenshots

A Playwright spec drives the app against its mocked API and writes the images to `.github/screenshots`:

```bash
cd skyline-web
SCREENSHOTS=1 pnpm exec playwright test e2e/screenshots.spec.ts
```

---

## Architecture

```
                    ┌────────────────────────┐
                    │  skyline-web (React)   │ :5173
                    │  nginx, /api → gateway │
                    └───────────┬────────────┘
                         ┌──────┴───────┐
                         │   Gateway    │ :8000
                         │ (routing,    │
                         │  rate limit) │
                         └──────┬───────┘
           ┌────────┬────────┬──┴──┬────────┬────────┬────────┐
           ▼        ▼        ▼     ▼        ▼        ▼        ▼
      ┌────────┐┌────────┐┌─────┐┌────┐┌──────────┐┌───────┐┌──────┐
      │ Fleet  ││ Route  ││Auth ││Sched││Reserv.   ││CheckIn││ Crew │
      │ :8081  ││ :8082  ││:8083││:8080││  :8084   ││ :8085 ││:8086 │
      └───┬────┘└───┬────┘└──┬──┘└──┬─┘└────┬─────┘└───┬───┘└──┬───┘
          │         │        │      │       │          │       │
      ┌───┴──┐  ┌───┴──┐ ┌──┴──┐┌──┴──┐┌───┴───┐ ┌───┴──┐┌───┴──┐
      │PG    │  │PG    │ │PG   ││PG   ││PG     │ │PG    ││PG    │
      │:5434 │  │:5435 │ │:5433││:5436││:5437  │ │:5438 ││:5439 │
      └──────┘  └──────┘ └─────┘└─────┘└───────┘ └──────┘└──────┘

           ┌─────────┐              ┌─────────┐       ┌──────────────┐  ┌───────┐  ┌──────────┐
           │  Redis  │ :6379        │  Kafka  │ :9092 │ Notification │──│PG     │  │ Mailpit  │
           └─────────┘              └────┬────┘       │    :8087     │  │:5440  │  │SMTP :1025│
                                         └───────────▶│ (consumer)   │──┼───────┼─▶│ UI :8025 │
                                                      └──────────────┘  └───────┘  └──────────┘
```

Schedule and reservation publish events through transactional outboxes (`skyline.flight.cancelled`, `skyline.flight.changed`, `skyline.booking.events`); reservation and notification consume them. The gateway also routes `/api/v1/notifications/**` to the notification service.

### Modules

| Module | Package | Purpose |
|---|---|---|
| `skyline-common` | `com.basarsy.skyline.common` | Shared library — JWT filter, `SecurityConfig`, `ApiResponse<T>`, `GlobalExceptionHandler`, Kafka topic config, Feign interceptors, `BaseEntity` |
| `skyline-gateway` | `com.basarsy.skyline.gateway` | Spring Cloud Gateway — routes all `/api/v1/**` traffic to the appropriate service; CORS; per-IP rate limits on login, registration, token refresh and PNR lookup |
| `skyline-auth` | `com.basarsy.skyline.user` | User accounts, registration, login, JWT token issuance and refresh |
| `skyline-fleet` | `com.basarsy.skyline.fleet` | Aircraft & aircraft type management |
| `skyline-route` | `com.basarsy.skyline.route` | Airports & route management |
| `skyline-schedule` | `com.basarsy.skyline.schedule` | Flight scheduling, status transitions, seat inventory |
| `skyline-reservation` | `com.basarsy.skyline.reservation` | Bookings, passengers, ticketing, PNR generation |
| `skyline-checkin` | `com.basarsy.skyline.checkin` | Online check-in & boarding passes |
| `skyline-crew` | `com.basarsy.skyline.crew` | Crew members & flight crew assignments |
| `skyline-notification` | `com.basarsy.skyline.notification` | Passenger notifications from Kafka events: booking confirmations, delays, gate changes, check-in reminders; email (SMTP) and in-app inbox |
| `skyline-e2e` | `com.basarsy.skyline.e2e` | End-to-end API tests through the gateway (`-Pe2e`) |
| `skyline-web` | — | React web app: passenger site and ops console ([README](skyline-web/README.md)) |

Each service owns its own `entity`, `dto`, `mapper`, `repository`, `service`, and `controller` packages. Cross-service communication uses **OpenFeign** HTTP clients — no shared entity references, only IDs cross boundaries.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Cloud | Spring Cloud 2025.0 (Gateway, OpenFeign) |
| Database | PostgreSQL 16 (one instance per service) |
| Messaging | Apache Kafka (Confluent 7.6, KRaft mode), transactional outboxes |
| Email | Spring Mail (SMTP); Mailpit as the local mail catcher |
| Caching | Redis 7 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Auth | Spring Security + JWT (jjwt 0.12.6) |
| Mapping | MapStruct 1.6 |
| Validation | Jakarta Bean Validation |
| Docs | SpringDoc OpenAPI / Swagger UI (schedule, checkin) |
| Build | Maven (multi-module aggregator) |
| CI | GitHub Actions (`./mvnw -B verify`) |
| Other | Lombok |
| Web app | React 19, TypeScript, Vite, React Router 7, TanStack Query |
| Web UI | Tailwind CSS 4, shadcn/ui (Radix), Motion, Lucide icons |
| Web tests | Vitest, MSW, Playwright with axe accessibility checks |
| Web serving | nginx (Docker), SPA fallback, `/api` proxied to the gateway |

---

## Domain Model

```
Airport ──────┐
              ├── Route ──────── Flight ──────── Reservation ──── BoardingPass
              └──────────────────────┘              │
                                                     └── Passenger ──── User
Aircraft ──────────── Flight
AircraftType ─────────────┘

FlightCrew (join) ──── Flight
                  └─── CrewMember
```

### Core Entities

**`Airport`** — IATA/ICAO codes, city, country, timezone

**`Aircraft`** — tail number, type, status `{ACTIVE, MAINTENANCE, RETIRED}`

**`Flight`** — route + aircraft + departure time + seat count + base price
- Status machine: `SCHEDULED → BOARDING → DEPARTED → ARRIVED`
- Side paths: `→ CANCELLED`, and `SCHEDULED → DELAYED → SCHEDULED/BOARDING` through the delay endpoint (a delay always has an expected time). Boarding is one-way: a boarding flight that is delayed stays `BOARDING`, so sales never reopen
- An aircraft flies one flight at a time, with 30 minutes on the ground in between (a delayed flight counts at its expected times); creating or editing a flight that breaks this is refused with `409`

**`Reservation`** — passenger + flight + cabin class + seat + PNR
- Status: `PENDING → CONFIRMED → CHECKED_IN`
- Side path: `→ CANCELLED`

**`BoardingPass`** — gate, boarding time, barcode, issued on check-in

**`CrewMember`** — employee number, role `{CAPTAIN, FIRST_OFFICER, PURSER, FLIGHT_ATTENDANT}`, license expiry

---

## REST API

All traffic is routed through the **API Gateway** at `http://localhost:8000`.

Base path: `/api/v1`

**CORS.** The gateway answers CORS for browser clients on the origins listed in `SKYLINE_CORS_ALLOWED_ORIGINS` (comma-separated, default `http://localhost:5173`, the web front-end's dev server). Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`; allowed request headers: `Authorization, Content-Type`; no credentials (tokens travel in the `Authorization` header); preflights are cached for an hour.

### Auth (skyline-auth · :8083)
```
POST   /auth/register              Register passenger
POST   /auth/login                 Login → JWT
POST   /auth/refresh               Refresh token
GET    /auth/me                    Current user                [any authenticated]
```

### Users (skyline-auth · :8083)
```
GET    /users                      Search users, paged (optional: email = contains,
                                   case-insensitive; role)     [ADMIN]
POST   /users                      Create a STAFF or ADMIN account
                                   (password min 12; duplicate email: 409) [ADMIN]
```

### Flights (skyline-schedule · :8080)
```
GET    /flights                    Search bookable flights: SCHEDULED + DELAYED (origin, destination IATA;
                                   date = calendar day at the origin airport, in its time zone;
                                   only flights whose sales are open, i.e. departing in over an hour)
GET    /flights/board              Operations flight board, every status [STAFF, ADMIN]
                                   (optional: date = UTC day, status (repeatable), flightNumber
                                    (contains, case-insensitive), routeId; paged, default size 50,
                                    sort departureTime,asc)
GET    /flights/{id}               Flight detail
POST   /flights                    Create flight (aircraft must be ACTIVE and free then) [STAFF, ADMIN]
PUT    /flights/{id}               Update flight (aircraft must be ACTIVE) [STAFF, ADMIN]; send the
                                   editVersion it was opened at: 409 if someone edited it since
PATCH  /flights/{id}/status        Update flight status (not DELAYED: use /delay) [STAFF, ADMIN; CANCELLED: ADMIN only,
                                   same full cancellation as DELETE]
PUT    /flights/{id}/delay         Delay: set estimatedDepartureTime (after the scheduled departure,
                                   by at most 24h); status becomes DELAYED (a BOARDING flight stays
                                   BOARDING)  [STAFF, ADMIN]
DELETE /flights/{id}/delay         Lift the delay; a DELAYED flight is SCHEDULED again  [STAFF, ADMIN]
PUT    /flights/{id}/gate          Assign or change the gate ("B12", "G5", "214A")  [STAFF, ADMIN]
DELETE /flights/{id}               Cancel flight               [ADMIN]
```

### Fleet (skyline-fleet · :8081)
```
GET    /aircraft                   List fleet                  [STAFF, ADMIN]
GET    /aircraft/{id}              Aircraft detail             [STAFF, ADMIN]
POST   /aircraft                   Add aircraft                [ADMIN]
PATCH  /aircraft/{id}/status       Update status               [STAFF, ADMIN; RETIRED: ADMIN only, and 409
                                   while the aircraft still has flights not cancelled or arrived]
GET    /aircraft-types             List aircraft types         [STAFF, ADMIN]
GET    /aircraft-types/{id}        Aircraft type detail        [STAFF, ADMIN]
POST   /aircraft-types             Add aircraft type           [ADMIN]
GET    /aircraft-types/{id}/layout Cabin layout as stored, for editing [STAFF, ADMIN]
PUT    /aircraft-types/{id}/layout Save a cabin layout (400 if its seat count ≠ total seats) [ADMIN]
GET    /aircraft-types/{id}/seat-layout Every seat, expanded       [STAFF, ADMIN, SERVICE]
```

### Routes & Airports (skyline-route · :8082)
```
GET    /airports                   List airports               [PUBLIC]
GET    /airports/{id}              Airport detail              [PUBLIC]
POST   /airports                   Create airport              [ADMIN]
GET    /routes                     List routes                 [PUBLIC]
GET    /routes/{id}                Route detail                [PUBLIC]
GET    /routes/search              Search routes (originIata, destinationIata) [PUBLIC]
POST   /routes                     Create route                [ADMIN]
```

### Reservations (skyline-reservation · :8084)
```
POST   /reservations               Book a flight (optional fareType) [PASSENGER]
POST   /reservations/group         Book passengers × legs with held seats and add-ons, all or nothing [PASSENGER]
GET    /reservations/{id}          Reservation detail          [PASSENGER, STAFF, ADMIN]
GET    /reservations/pnr/{pnr}     Look up by PNR              [PUBLIC: ?lastName= required, redacted view; owner/STAFF/ADMIN: full]
GET    /reservations/my            My bookings                 [PASSENGER]
DELETE /reservations/{id}          Cancel reservation          [PASSENGER, STAFF, ADMIN]

GET    /flights/{id}/seat-map      Layout, live availability, this passenger's prices [PASSENGER, STAFF, ADMIN]
                                   (?reservationId=, or while booking ?cabin=&fareType=&passenger=0..8)
POST   /reservations/seat-holds    Hold a seat while booking (15 min; 409 if someone else has it) [PASSENGER]
DELETE /reservations/seat-holds    Release it (?flightId=&passenger=) [PASSENGER]
POST   /reservations/{id}/seat-hold Hold a seat for a booked passenger [owner, STAFF, ADMIN]
DELETE /reservations/{id}/seat-hold Release it                  [owner, STAFF, ADMIN]
PUT    /reservations/{id}/seat     Confirm the held seat (charges its fee) [owner, STAFF, ADMIN]
DELETE /reservations/{id}/seat     Give the seat back           [owner, STAFF, ADMIN]

GET    /reservations/{id}/ancillaries/options What can be bought, prices, what the fare includes [owner, STAFF, ADMIN]
POST   /reservations/{id}/ancillaries Buy add-ons (priced on the server; channel AIRPORT: STAFF/ADMIN) [owner, STAFF, ADMIN]
DELETE /reservations/{id}/ancillaries/{lineId} Remove an add-on before its cut-off [owner, STAFF, ADMIN]

GET    /seat-price-rules           Seat price table             [STAFF, ADMIN]
PUT    /seat-price-rules/{id}      Edit a price / auto-assign flag [ADMIN]
GET    /ancillary-products         Add-on catalog with prices and fare allowances [PASSENGER, STAFF, ADMIN]
PUT    /ancillary-products/{code}  Edit a product              [ADMIN]
PUT    /ancillary-products/{code}/prices/{DOMESTIC|INTERNATIONAL} Edit its online and airport price [ADMIN]
```

### Check-In (skyline-checkin · :8085)
```
POST   /checkin                    Online check-in
GET    /checkin/{pnr}/boarding-pass Download boarding pass
```

### Crew (skyline-crew · :8086)
```
GET    /crew                       Crew directory, sorted by last name [STAFF, ADMIN]
                                   (optional: role, q = first/last name or employee number)
GET    /crew/{id}                  Crew member detail          [STAFF, ADMIN]
POST   /crew                       Add crew member             [ADMIN] (duplicate employee number: 409)
POST   /flights/{id}/crew          Assign to flight            [STAFF, ADMIN]
GET    /flights/{id}/crew          Crew manifest               [STAFF, ADMIN]
DELETE /flights/{flightId}/crew/{crewMemberId}  Remove from flight [STAFF, ADMIN]
```

### Notifications (skyline-notification · :8087)
```
GET    /notifications              My in-app notifications, newest first (page, size ≤ 50)  [signed in]
GET    /notifications/unread-count { count }                                                 [signed in]
POST   /notifications/{id}/read    Mark one read (someone else's: 404)                       [signed in]
POST   /notifications/read-all     Mark all read                                             [signed in]
```

Bookings (`POST /reservations`, `POST /reservations/group`) take an optional `contactEmail`: where the confirmation and flight changes are emailed. It defaults to the account's email and is left out of the public PNR view.

### Dates and times

**All date-times are UTC.** Every `LocalDateTime` the API accepts, stores, compares or returns (`departureTime`, `arrivalTime`, `bookedAt`, `boardingTime`, `issuedAt`, `createdAt`, the envelope `timestamp`, ...) is a UTC wall-clock time, serialized as ISO-8601 **without an offset**, e.g. `"2026-12-01T14:30:00"`. Clients send UTC the same way (no `Z`, no offset).

- Every service pins its JVM to UTC (`TimeZone.setDefault` in `main`, `-Duser.timezone=UTC` and `TZ=UTC` in the Docker image) and Hibernate writes timestamps in UTC (`hibernate.jdbc.time_zone: UTC`), so a service started on a non-UTC host behaves identically.
- Airports carry their IANA zone, and every embedded route exposes it as `origin.timezone` / `destination.timezone`, so clients can show departure in the origin's local time and arrival in the destination's.
- `LocalDate` values (`dateOfBirth`, `licenseExpiryDate`) have no zone.
- The flight search `date` is a **calendar day at the origin airport**: schedule turns it into the UTC range from local midnight to the next local midnight, using the origin's zone from the route service (23 or 25 hours on DST changes). A 01:30 departure from Istanbul on the 2nd (22:30 UTC on the 1st) is listed under the 2nd. An airport without a valid zone falls back to the UTC day (and logs a warning). The flight board has no origin, so its `date` stays a UTC day.
- The check-in window (48h → 1h before departure) is evaluated against a UTC clock.

### Money

Prices (`basePrice` on a flight, `totalPrice` on a reservation) are JSON numbers, and every response that carries one also carries `currency`, the ISO 4217 code configured with `SKYLINE_CURRENCY` (default `EUR`).

### Response Envelope

All endpoints return a consistent `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Flight booked successfully",
  "data": { ... },
  "timestamp": "2025-06-01T14:30:00",
  "errors": []
}
```

---

## Business Logic Highlights

**Pricing**
- Base price × cabin multiplier (Economy `1.0×`, Business `2.5×`, First `4.0×`) × fare (`BASIC 0.85×`, `STANDARD 1.0×`, `FLEX 1.3×`)
- Load factor surcharge: `>80%` full → `+15%`, `>90%` full → `+30%`
- A reservation's `totalPrice` is its `farePrice` plus its active add-on lines (seat fee, bags, ...)

**Cabin layouts and seats**
- Each aircraft type stores a compact cabin layout (fleet, `aircraft_types.cabin_layout`): sections with row ranges and seat letters grouped by aisle (`ABC|DEF`, `AC|DG|HK`, ...), exits, wing, lavatories/galleys/closets, omitted and accessible seats. `SeatLayoutExpander` derives every seat's position, legroom, flags and price category; a layout must have exactly the type's `total_seats`
- Seeded: A320neo (Y180, 3-3), 737-800 (Y189, 3-3), 787-9 (C30 2-2-2 + Y266 3-3-3) and A321neo (J16 2-2 + Y174 3-3)
- Availability is derived per flight: `TAKEN` from reservations, `HELD` from Redis holds, `BLOCKED` from `blocked_seats`
- Each cabin sells at most its seats (an advisory lock serializes the count): no Business on an all-Economy aircraft. Types without a layout keep the old behaviour

**Seat prices**
- `seat_price_rules` (admin-editable): cabin × fare × category (`EXIT_ROW`, `EXTRA_LEGROOM`, `FRONT`, `PREFERRED` = window/aisle, `STANDARD`). Economy BASIC pays for every seat, STANDARD gets ordinary seats free, FLEX pays only for extra legroom; Business and First include every seat. Paid seats get the same nearly-full surcharge as fares
- Prices are computed on the server only; a passenger can only choose a seat in the cabin they booked (422 otherwise)

**Seat holds and double booking**
- Choosing a seat holds it in Redis (`SET NX`, 15 min, `SKYLINE_SEAT_HOLD_TTL`) for one passenger slot; picking another seat releases the previous one atomically (Lua). A seat someone else holds or booked is a 409, and the client reloads the map
- The final guard is the unique `(flight_id, seat_number)` constraint: a confirmation that loses a race is a 409 too
- Seats can be chosen or changed until check-in, and never after check-in closes; a checked-in seat is final (it is on the boarding pass)

**Seat assignment at check-in**
- A passenger without a seat gets a random free seat in their own cabin, only from categories the price table marks `auto_assign` (ordinary seats, never paid extra-legroom or exit rows unless nothing else is left), skipping taken, blocked and held seats, and as close as possible to the others of the same booking group

**Bags and add-ons**
- One generic model: `ancillary_products` (cabin bag, checked bags 15/20/30 kg, +10/+20 kg, sports equipment, priority boarding, meals), `ancillary_prices` per route type (domestic when both airports are in one country) with a higher airport price, `fare_allowances` for what each fare includes, and `reservation_ancillaries` for what was bought (the seat fee is a line too)
- Per passenger per flight, at booking, in manage booking and at check-in. What the fare includes can't be bought again; at most 3 checked bags, one top-up per bag, one meal; meals close 24h before departure, the rest 1h
- Cancelling refunds add-ons on FLEX fares and always-refundable products (meals); an airline cancellation refunds every add-on

**Seat Availability**
- Optimistic locking (`@Version`) prevents double-booking under concurrent requests
- Atomic decrement via `@Modifying` JPQL query
- Cross-service seat management via internal OpenFeign calls (schedule ↔ reservation). Each booking holds its seat under a **claim** id the reservation service chooses (`PUT/DELETE /flights/{id}/inventory/claims/{claimId}`, `seat_claims` in schedule): claiming twice takes one seat, releasing a claim that never landed gives nothing back, and a claim that arrives after its own release is refused. The release is registered before the claim call, so a booking that rolls back (even after a timed-out claim) returns its seat, and a cancellation releases it by the id kept on the reservation. When a commit's outcome is unknown the seat is kept and `SEAT_CLAIM_UNKNOWN` is logged; a failed release logs `SEAT_RELEASE_FAILED`, and releasing again is safe
- Sales close **1 hour** before the scheduled departure, with check-in and online add-ons: search stops listing the flight and booking it is refused, so every seat sold can still be checked in

**Check-In Window**
- Opens **48 hours** before departure
- Closes **1 hour** before departure
- Violations throw `CheckInWindowException` (HTTP 422)

**PNR Generation**
- 6-character alphanumeric, derived from UUID prefix
- Unique constraint enforced at DB level

**Crew Validation**
- A flight cannot depart without at least 1 `CAPTAIN` + 1 `FIRST_OFFICER`
- Crew members cannot be double-assigned to overlapping flights
- License expiry is validated at assignment time

**Delays and gates**
- `departureTime`/`arrivalTime` are always the scheduled times. A delay sets `estimatedDepartureTime` (and the response derives `estimatedArrivalTime` by the same shift); lifting it, moving a DELAYED flight back to SCHEDULED, or rescheduling the flight clears it
- Search, the check-in window and add-on cut-offs keep using the scheduled departure; the boarding pass shows the current gate and boards 45 minutes before the expected departure, also on a pass issued before the change
- A flight without a gate falls back to a stable placeholder gate on its boarding pass

**Flight change events**
- Every change a passenger should hear about is recorded through the same outbox as a `FlightChangedEvent` on `skyline.flight.changed` (keyed by flight id, so in order per flight): `STATUS_CHANGED`, `DELAYED`, `DELAY_CLEARED`, `RESCHEDULED`, `GATE_CHANGED`, `CANCELLED`
- Each event is self-contained (flight number, route IATA codes, current and previous status, times and gate), so a consumer can describe the change without calling the schedule service back. Nothing is recorded when an operation changes nothing
- It is a separate topic from `skyline.flight.cancelled` on purpose: the reservation service cancels bookings for any record on that topic

**Notifications**
- The notification service keeps its own projection of who is booked on which flight, from the booking events (`BOOKED` once per booking with every reservation, `CANCELLED` per reservation a passenger cancels), so a flight change needs no call back to the reservation service. Bookings made before the service first ran are not in it
- Email and in-app: booking confirmed or cancelled, delay (new local time), delay lifted, reschedule, gate change, airline cancellation, and "check-in is open" once per booking when the 48h window opens. In-app only: boarding started. Nothing for departed or arrived
- One email per contact address and one inbox entry per account, listing the passengers they cover: a family booked together gets one message, not one each
- Events arrive at least once; every message has a dedupe key (event + recipient, the address as a SHA-256 digest), so a redelivery sends nothing new. A record that can't be handled is retried with growing waits adding up to 5 minutes (enough to ride out a database or broker restart; the records behind it on its partition wait), then parked on `<topic>.DLT` with a `KAFKA_RECORD_PARKED` log line; malformed JSON and constraint violations are parked at once (see [Dead-lettered records](#dead-lettered-records))
- A reschedule moves the check-in reminder with the flight: one already sent before the new window opened goes out again when it does
- Emails are queued in the same transaction (`email_outbox`) and sent by a scheduled job with exponential backoff, so an SMTP outage delays emails instead of losing them. Locally, Mailpit catches every email (inbox at `http://localhost:8025`); nothing leaves the machine

**Flight Cancellation**
- Cancelling a flight resets available seats and records a `FlightCancelledEvent` in the schedule service's `outbox_events` table, in the same transaction as the status change
- A scheduled outbox publisher sends pending events to Kafka (`skyline.flight.cancelled` topic, keyed by flight id) and retries with backoff while Kafka is unreachable, so no cancellation is lost and the cancel request never waits on Kafka (`skyline.outbox.*` settings)
- The reservation service listens for these events via `FlightCancellationListener`; delivery is at least once, which is safe because it only cancels reservations that are not cancelled yet

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for PostgreSQL, Redis, Kafka)

### Run with Docker (full system)

```bash
# Clone the repository
git clone https://github.com/your-org/skyline.git
cd skyline

# Create your environment file
cp .env.example .env
# Edit .env and set JWT_SECRET, SKYLINE_INTERNAL_API_KEY, POSTGRES_PASSWORD and REDIS_PASSWORD
# (each: openssl rand -hex 32)

# Build and start everything (infrastructure + all services)
docker compose up --build --wait   # returns once every service reports healthy
```

The web app is served at `http://localhost:5173` and the API Gateway at `http://localhost:8000`. Set `SKYLINE_ADMIN_EMAIL` and `SKYLINE_ADMIN_PASSWORD` in `.env` to get an admin account for the ops console; passengers register on the site.

### Run Locally (services on bare metal, infrastructure in Docker)

```bash
# Clone the repository
git clone https://github.com/your-org/skyline.git
cd skyline

# Start infrastructure only (8× PostgreSQL, Redis, Kafka, Mailpit)
docker compose up -d auth-db fleet-db route-db schedule-db reservation-db checkin-db crew-db notification-db redis kafka mailpit

# Configure secrets (see .env.example) and export them in each terminal
cp .env.example .env   # then fill in JWT_SECRET, SKYLINE_INTERNAL_API_KEY, POSTGRES_PASSWORD, REDIS_PASSWORD
set -a; source .env; set +a

# Build all modules
./mvnw -B install -DskipTests

# Start each service (in separate terminals)
./mvnw -pl skyline-auth spring-boot:run
./mvnw -pl skyline-fleet spring-boot:run
./mvnw -pl skyline-route spring-boot:run
./mvnw -pl skyline-schedule spring-boot:run
./mvnw -pl skyline-reservation spring-boot:run
./mvnw -pl skyline-checkin spring-boot:run
./mvnw -pl skyline-crew spring-boot:run
./mvnw -pl skyline-notification spring-boot:run
./mvnw -pl skyline-gateway spring-boot:run
```

The API Gateway will be available at `http://localhost:8000`.

Swagger UI (schedule service): `http://localhost:8080/swagger-ui.html`
Swagger UI (checkin service): `http://localhost:8085/swagger-ui.html`

### Service Ports

| Service | App Port | DB Port | Database Name |
|---|---|---|---|
| skyline-auth | 8083 | 5433 | `skyline_auth` |
| skyline-fleet | 8081 | 5434 | `skyline_fleet` |
| skyline-route | 8082 | 5435 | `skyline_route` |
| skyline-schedule | 8080 | 5436 | `skyline_schedule` |
| skyline-reservation | 8084 | 5437 | `skyline_reservation` |
| skyline-checkin | 8085 | 5438 | `skyline_checkin` |
| skyline-crew | 8086 | 5439 | `skyline_crew` |
| skyline-notification | 8087 | 5440 | `skyline_notification` |
| skyline-gateway | 8000 | — | — |
| Redis | 6379 | — | — |
| Kafka | 9092 | — | — |
| Mailpit (SMTP / inbox UI) | 1025 / 8025 | — | — |

`docker compose` publishes every port, the web app's `5173` included, on `127.0.0.1` only, so none of them is reachable from the network. Redis and PostgreSQL require the passwords from `.env`.

### Health Checks

Every Java service (and the gateway) exposes Spring Boot Actuator health on its own port; nothing else from Actuator is reachable over HTTP, and responses carry only a status, never details:

| Endpoint | Meaning |
|---|---|
| `GET /actuator/health` | Overall status (`UP` / `DOWN`) |
| `GET /actuator/health/liveness` | The JVM is alive; restart it if this fails |
| `GET /actuator/health/readiness` | Ready for traffic (services: including their database) |

The Docker image's healthcheck polls `/actuator/health/readiness`, and compose starts each service only after the services it calls are healthy. The gateway does not route `/actuator/**`, so a service's endpoints are not reachable through port 8000 (`localhost:8000/actuator/health` is the gateway's own).

### Environment Variables

Each service reads configuration from its own `application.yml`. Infrastructure settings have local-development defaults; **secrets do not**, and services refuse to start without them:

| Variable | Description | Default |
|---|---|---|
| `DATABASE_URL` | JDBC URL (per-service) | `jdbc:postgresql://localhost:<port>/<db>` |
| `DATABASE_USERNAME` | DB user | `skyline_user` |
| `POSTGRES_PASSWORD` | **Required by compose.** Password compose creates every database with, and passes to the services as `DATABASE_PASSWORD` | — (compose refuses to start) |
| `DATABASE_PASSWORD` | DB password | `POSTGRES_PASSWORD`, else empty |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | **Required by compose.** Redis `requirepass`; used by fleet, route, crew (caches), reservation (seat holds) and the gateway (rate limits) | — (compose refuses to start) |
| `JWT_SECRET` | **Required.** HS256 signing key, min 32 bytes, identical across services | — (startup fails) |
| `SKYLINE_INTERNAL_API_KEY` | **Required.** Shared service-to-service key (grants `ROLE_SERVICE`), min 32 bytes | — (startup fails) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `JWT_EXPIRY_MS` | Access token TTL in ms | `900000` |
| `JWT_REFRESH_EXPIRY_MS` | Refresh token TTL in ms | `604800000` |
| `FLEET_SERVICE_URL` | Fleet service base URL | `http://localhost:8081` |
| `ROUTE_SERVICE_URL` | Route service base URL | `http://localhost:8082` |
| `CREW_SERVICE_URL` | Crew service base URL | `http://localhost:8086` |
| `NOTIFICATION_SERVICE_URL` | (gateway) Notification service base URL | `http://localhost:8087` |
| `SMTP_HOST` / `SMTP_PORT` | (notification) SMTP server for emails; compose points them at Mailpit | `localhost` / `1025` |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | (notification) SMTP credentials, for a real server | — |
| `SKYLINE_MAIL_FROM` | (notification) Sender of every email | `Skyline <no-reply@skyline.local>` |
| `SKYLINE_WEB_BASE_URL` | (notification) Base of the links in emails | `http://localhost:5173` |
| `SKYLINE_ADMIN_EMAIL` | (auth) Email of the initial ADMIN account, created on startup if missing | — (skipped) |
| `SKYLINE_ADMIN_PASSWORD` | (auth) Password for the initial ADMIN account (min 12 chars) | — (skipped) |
| `SKYLINE_CORS_ALLOWED_ORIGINS` | (gateway) Comma-separated browser origins allowed by CORS | `http://localhost:5173` |
| `SKYLINE_RATE_LIMIT_REPLENISH` | (gateway) Requests per second per client IP on `POST /auth/login`, `/auth/register`, `/auth/refresh` and `GET /reservations/pnr/**`; over the limit the gateway answers `429` | `5` |
| `SKYLINE_RATE_LIMIT_BURST` | (gateway) Bucket size: how many of those requests a client may send at once | `10` |
| `SKYLINE_RATE_LIMIT_TRUSTED_PROXIES` | (gateway) Reverse proxies in front of the gateway whose `X-Forwarded-For` entry identifies the client; `0` uses the TCP peer. Compose sets `1` for the web container's nginx | `0` |
| `SPRING_PROFILES_ACTIVE` | `dev` logs every SQL statement (`show-sql`) | — |
| `SKYLINE_CURRENCY` | (schedule, reservation) ISO 4217 currency of every price, returned as `currency`; validated at startup. Set the same value on both | `EUR` |
| `SKYLINE_SEAT_HOLD_TTL` | (reservation) How long a chosen seat stays held, ISO-8601 duration | `PT15M` |
| `SKYLINE_FEIGN_CONNECT_TIMEOUT_MS` / `SKYLINE_FEIGN_READ_TIMEOUT_MS` | (every service) Timeouts of calls to other services | `2000` / `5000` |
| `SKYLINE_GATEWAY_CONNECT_TIMEOUT_MS` / `SKYLINE_GATEWAY_RESPONSE_TIMEOUT` | (gateway) Timeouts of routed requests; past the response timeout the gateway answers `504` | `2000` / `30s` |
| `SKYLINE_KAFKA_CONSUMERRETRY_*` | (reservation, notification) `INITIALINTERVAL`, `MULTIPLIER`, `MAXINTERVAL`, `MAXELAPSEDTIME` (`skyline.kafka.consumer-retry.*`): how a failing record is retried before it is dead-lettered | `1s`, `2`, `60s`, `5m` |

> Public registration always creates `PASSENGER` accounts. Set `SKYLINE_ADMIN_EMAIL` / `SKYLINE_ADMIN_PASSWORD` on the auth service to bootstrap the first `ADMIN`, who can then create further `STAFF` and `ADMIN` accounts with `POST /users`.

---

## Database Migrations

Each service manages its own Flyway migrations, located at:

```
skyline-<module>/src/main/resources/db/migration/<module>/
```

They are packaged with the service and applied on startup (`classpath:db/migration/<module>`), so that directory is the only copy.

**Never edit an existing migration.** Always create a new versioned file.

---

## Dead-lettered records

A record a consumer still can't handle after retrying (waits adding up to 5 minutes) is parked on `<topic>.DLT` (same partition), and the service logs `KAFKA_RECORD_PARKED topic=… partition=… offset=…`: alert on that line. Parked records are not processed again on their own. Once the cause is fixed, replay them onto the original topic; every consumer is idempotent, so a record handled twice changes nothing:

```bash
# Look at what is parked
docker exec skyline-kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic skyline.flight.cancelled.DLT --from-beginning --property print.key=true --timeout-ms 5000

# Replay it (keys kept, so per-flight ordering holds)
docker exec skyline-kafka sh -c "kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic skyline.flight.cancelled.DLT --from-beginning --property print.key=true --property key.separator='|' \
  --timeout-ms 5000 | kafka-console-producer --bootstrap-server localhost:9092 \
  --topic skyline.flight.cancelled --property parse.key=true --property key.separator='|'"
```

---

## Running Tests

### Unit and integration tests

```bash
# All module tests (from project root); Testcontainers tests need Docker
./mvnw verify
```

### End-to-end tests (`skyline-e2e`)

The `skyline-e2e` module drives the **running** system through the API gateway: booking, PNR lookup,
check-in and seat assignment, flight cancellation over Kafka, crew manifests and security rules.
It only runs with the `e2e` profile and needs the stack to be up:

```bash
cp .env.example .env            # set the secrets, SKYLINE_ADMIN_EMAIL and SKYLINE_ADMIN_PASSWORD, and raise
                                # SKYLINE_RATE_LIMIT_REPLENISH / _BURST (e.g. 1000 / 2000): the suite logs in a lot
set -a; source .env; set +a
docker compose up -d --build --wait
./mvnw -Pe2e verify -pl skyline-e2e
```

Each test creates its own aircraft, flights and users, so the suite can run repeatedly against the same stack.
Override the target with `-Dskyline.e2e.baseUrl=...` if the gateway is not on `localhost:8000`.

The suite connects to the auth database on `localhost:5433` with `POSTGRES_PASSWORD` from the environment.

CI runs `./mvnw -B verify` on every push and PR to `main`, then an `e2e` job that boots the stack with
throwaway secrets (and high rate limits) and runs the end-to-end suite. A `web` job lints, type-checks,
unit-tests and builds `skyline-web` and runs its Playwright suite against the mocked API. CodeQL scans the
Java and TypeScript code, and Dependabot proposes weekly dependency updates.

---

## Key Architectural Decisions

**Microservices with database-per-service** — Each bounded context owns its data. No shared tables, only IDs cross service boundaries via OpenFeign HTTP calls.

**Spring Cloud Gateway** — Single entry point on port 8000 routes traffic to the correct service based on URL path predicates.

**Kafka for async events** — Flight cancellations and changes (schedule) and booking events (reservation) are published to Kafka through a transactional outbox (`skyline-common`, opt-in per service with `skyline.outbox.enabled`; no event is lost while Kafka is down), decoupling the services for eventual consistency.

**UUID primary keys** — avoids sequential ID enumeration, portable across services.

**Records for request DTOs** — immutable, concise, communicates "this is data, not behaviour."

**Separate Request/Response types** — input and output shapes evolve independently.

**Package-by-feature** — `com.basarsy.skyline.fleet` contains everything about fleet management. No horizontal `service`/`repository` packages shared across domains.

**`spring.jpa.open-in-view: false`** — prevents Hibernate sessions from leaking through the HTTP layer.

**`@Enumerated(EnumType.STRING)`** — adding enum values won't silently break existing data.

---

<div align="center">

*Built with discipline. Designed to scale.*

**SKYLINE** · Airline Management System

</div>