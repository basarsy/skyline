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

*A microservices-based REST API for end-to-end airline operations*

---

![Java](https://img.shields.io/badge/Java-21-000000?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-000000?style=flat-square&logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2024.0-000000?style=flat-square&logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-000000?style=flat-square&logo=postgresql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Kafka-7.6-000000?style=flat-square&logo=apachekafka&logoColor=white)
![Status](https://img.shields.io/badge/Status-In_Development-555555?style=flat-square)

</div>

---

## Overview

Skyline is a backend REST API that manages the full lifecycle of airline operations — from aircraft fleet management and route scheduling to passenger reservations, check-in, and crew assignments.

The project is built as a **microservices architecture**: each bounded context runs as an independent Spring Boot application with its own database, communicating over HTTP (OpenFeign) and asynchronous events (Kafka). A Spring Cloud Gateway provides a unified API entry point.

---

## Architecture

```
                         ┌──────────────┐
                         │   Gateway    │ :8000
                         │  (routing)   │
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

           ┌─────────┐              ┌─────────┐
           │  Redis  │ :6379        │  Kafka  │ :9092
           └─────────┘              └─────────┘
```

### Modules

| Module | Package | Purpose |
|---|---|---|
| `skyline-common` | `com.basarsy.skyline.common` | Shared library — JWT filter, `SecurityConfig`, `ApiResponse<T>`, `GlobalExceptionHandler`, Kafka topic config, Feign interceptors, `BaseEntity` |
| `skyline-gateway` | `com.basarsy.skyline.gateway` | Spring Cloud Gateway — routes all `/api/v1/**` traffic to the appropriate service |
| `skyline-auth` | `com.basarsy.skyline.user` | User accounts, registration, login, JWT token issuance and refresh |
| `skyline-fleet` | `com.basarsy.skyline.fleet` | Aircraft & aircraft type management |
| `skyline-route` | `com.basarsy.skyline.route` | Airports & route management |
| `skyline-schedule` | `com.basarsy.skyline.schedule` | Flight scheduling, status transitions, seat inventory |
| `skyline-reservation` | `com.basarsy.skyline.reservation` | Bookings, passengers, ticketing, PNR generation |
| `skyline-checkin` | `com.basarsy.skyline.checkin` | Online check-in & boarding passes |
| `skyline-crew` | `com.basarsy.skyline.crew` | Crew members & flight crew assignments |

Each service owns its own `entity`, `dto`, `mapper`, `repository`, `service`, and `controller` packages. Cross-service communication uses **OpenFeign** HTTP clients — no shared entity references, only IDs cross boundaries.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Cloud | Spring Cloud 2024.0 (Gateway, OpenFeign) |
| Database | PostgreSQL 16 (one instance per service) |
| Messaging | Apache Kafka (Confluent 7.6, KRaft mode) |
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
- Side paths: `→ DELAYED`, `→ CANCELLED`

**`Reservation`** — passenger + flight + cabin class + seat + PNR
- Status: `PENDING → CONFIRMED → CHECKED_IN`
- Side path: `→ CANCELLED`

**`BoardingPass`** — gate, boarding time, barcode, issued on check-in

**`CrewMember`** — employee number, role `{CAPTAIN, FIRST_OFFICER, PURSER, FLIGHT_ATTENDANT}`, license expiry

---

## REST API

All traffic is routed through the **API Gateway** at `http://localhost:8000`.

Base path: `/api/v1`

### Auth (skyline-auth · :8083)
```
POST   /auth/register              Register passenger
POST   /auth/login                 Login → JWT
POST   /auth/refresh               Refresh token
```

### Flights (skyline-schedule · :8080)
```
GET    /flights                    Search flights (origin, destination, date)
GET    /flights/{id}               Flight detail
POST   /flights                    Create flight               [STAFF, ADMIN]
PUT    /flights/{id}               Update flight               [STAFF, ADMIN]
PATCH  /flights/{id}/status        Update flight status        [STAFF, ADMIN]
DELETE /flights/{id}               Cancel flight               [ADMIN]
```

### Fleet (skyline-fleet · :8081)
```
GET    /aircraft                   List fleet                  [STAFF, ADMIN]
GET    /aircraft/{id}              Aircraft detail             [STAFF, ADMIN]
POST   /aircraft                   Add aircraft                [ADMIN]
PATCH  /aircraft/{id}/status       Update status               [STAFF, ADMIN]
GET    /aircraft-types             List aircraft types         [STAFF, ADMIN]
GET    /aircraft-types/{id}        Aircraft type detail        [STAFF, ADMIN]
POST   /aircraft-types             Add aircraft type           [ADMIN]
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
POST   /reservations               Book a flight               [USER]
GET    /reservations/{id}          Reservation detail          [USER, STAFF, ADMIN]
GET    /reservations/pnr/{pnr}     Look up by PNR              [PUBLIC]
GET    /reservations/my            My bookings                 [USER]
DELETE /reservations/{id}          Cancel reservation          [USER, STAFF, ADMIN]
```

### Check-In (skyline-checkin · :8085)
```
POST   /checkin                    Online check-in
GET    /checkin/{pnr}/boarding-pass Download boarding pass
```

### Crew (skyline-crew · :8086)
```
POST   /crew                       Add crew member             [ADMIN]
POST   /flights/{id}/crew          Assign to flight            [STAFF, ADMIN]
GET    /flights/{id}/crew          Crew manifest               [STAFF, ADMIN]
DELETE /flights/{flightId}/crew/{crewMemberId}  Remove from flight [STAFF, ADMIN]
```

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
- Base price × cabin multiplier (Economy `1.0×`, Business `2.5×`, First `4.0×`)
- Load factor surcharge: `>80%` full → `+15%`, `>90%` full → `+30%`

**Seat Availability**
- Optimistic locking (`@Version`) prevents double-booking under concurrent requests
- Atomic decrement via `@Modifying` JPQL query
- Cross-service seat management via internal OpenFeign calls (schedule ↔ reservation)

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

**Flight Cancellation**
- Cancelling a flight resets available seats and publishes a `FlightCancelledEvent` to Kafka (`skyline.flight.cancelled` topic)
- The reservation service listens for these events via `FlightCancellationListener`

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for PostgreSQL, Redis, Kafka)

### Run Locally

```bash
# Clone the repository
git clone https://github.com/your-org/skyline.git
cd skyline

# Start infrastructure (7× PostgreSQL, Redis, Kafka)
docker compose up -d

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
| skyline-gateway | 8000 | — | — |
| Redis | 6379 | — | — |
| Kafka | 9092 | — | — |

### Environment Variables

Each service reads configuration from its own `application.yml` with sensible defaults for local development. Override via environment variables when needed:

| Variable | Description | Default |
|---|---|---|
| `DATABASE_URL` | JDBC URL (per-service) | `jdbc:postgresql://localhost:<port>/<db>` |
| `DATABASE_USERNAME` | DB user | `skyline_user` |
| `DATABASE_PASSWORD` | DB password | `skyline_pass` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `JWT_SECRET` | HS256 signing key (min 32 chars) | dev-only placeholder |
| `JWT_EXPIRY_MS` | Access token TTL in ms | `900000` |
| `JWT_REFRESH_EXPIRY_MS` | Refresh token TTL in ms | `604800000` |
| `FLEET_SERVICE_URL` | Fleet service base URL | `http://localhost:8081` |
| `ROUTE_SERVICE_URL` | Route service base URL | `http://localhost:8082` |
| `CREW_SERVICE_URL` | Crew service base URL | `http://localhost:8086` |

---

## Database Migrations

Each service manages its own Flyway migrations, located at:

```
skyline-<module>/src/main/resources/db/migration/<module>/
```

A top-level `db-migrations/` directory contains a reference copy of all migration scripts organized by service:

```
db-migrations/
├── auth/        V1__init.sql
├── fleet/       V1__init.sql, V2__seed_aircraft_types.sql
├── route/       V1__init.sql, V2__seed_airports.sql
├── schedule/    V1__init.sql
├── reservation/ V1__init.sql
├── checkin/     V1__init.sql
└── crew/        V1__init.sql
```

**Never edit an existing migration.** Always create a new versioned file.

---

## Running Tests

```bash
# All tests (from project root)
./mvnw test
```

> **Note:** The test suite is still being built out. CI runs `./mvnw -B verify` on every push and PR to `main`.

---

## Key Architectural Decisions

**Microservices with database-per-service** — Each bounded context owns its data. No shared tables, only IDs cross service boundaries via OpenFeign HTTP calls.

**Spring Cloud Gateway** — Single entry point on port 8000 routes traffic to the correct service based on URL path predicates.

**Kafka for async events** — Flight cancellation events are published to Kafka, decoupling the schedule and reservation services for eventual consistency.

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