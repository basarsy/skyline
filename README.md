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

*A production-grade REST API for end-to-end airline operations*

---

![Java](https://img.shields.io/badge/Java-21-000000?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-000000?style=flat-square&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-000000?style=flat-square&logo=postgresql&logoColor=white)
![Status](https://img.shields.io/badge/Status-In_Development-555555?style=flat-square)

</div>

---

## Overview

Skyline is a backend REST API that manages the full lifecycle of airline operations. From aircraft fleet management and route scheduling to passenger reservations, check-in, and crew assignments — Skyline handles it all through a clean, layered architecture built on SOLID principles.

The project is structured as a **modular monolith**: bounded contexts enforce clear boundaries today, and make microservice extraction straightforward tomorrow.

---

## Architecture

```
com.skyline
│
├── common/              # Cross-cutting concerns
│   ├── exception/       # GlobalExceptionHandler, custom exceptions
│   ├── response/        # ApiResponse<T> envelope
│   └── security/        # JWT filter, SecurityConfig
│
├── fleet/               # Aircraft & fleet management
├── route/               # Airports & routes
├── schedule/            # Flight scheduling
├── reservation/         # Bookings & ticketing
├── checkin/             # Check-in & boarding passes
├── crew/                # Crew members & assignments
└── user/                # Accounts & authentication
```

Each bounded context owns its own `entity`, `dto`, `mapper`, `repository`, `service`, and `controller` packages. No cross-context entity references — only IDs cross boundaries.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Auth | Spring Security |
| Validation | Jakarta Bean Validation |
| Docs | SpringDoc OpenAPI / Swagger UI |
| Testing | JUnit 5, Mockito |
| Build | Maven |

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
                  └─── CrewMember ──── User
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

Base URL: `/api/v1`

### Flights
```
GET    /flights                    Search flights (origin, dest, date)
GET    /flights/{id}               Flight detail
POST   /flights                    Create flight           [STAFF]
PUT    /flights/{id}               Update flight           [STAFF]
PATCH  /flights/{id}/status        Update flight status    [STAFF]
DELETE /flights/{id}               Cancel flight           [ADMIN]
```

### Reservations
```
POST   /reservations               Book a flight           [USER]
GET    /reservations/{id}          Reservation detail      [USER]
GET    /reservations/pnr/{pnr}     Look up by PNR          [PUBLIC]
GET    /reservations/my            My bookings             [USER]
DELETE /reservations/{id}          Cancel reservation      [USER]
```

### Check-In
```
POST   /checkin                    Online check-in         [USER]
GET    /checkin/{pnr}/boarding-pass Download boarding pass [USER]
```

### Fleet
```
GET    /aircraft                   List fleet              [STAFF]
GET    /aircraft/{id}              Aircraft detail         [STAFF]
POST   /aircraft                   Add aircraft            [ADMIN]
PATCH  /aircraft/{id}/status       Update status           [STAFF]
GET    /aircraft-types             List aircraft types     [STAFF]
GET    /aircraft-types/{id}        Aircraft type detail    [STAFF]
POST   /aircraft-types             Add aircraft type       [ADMIN]
GET    /airports                   List airports           [PUBLIC]
GET    /routes                     List routes             [PUBLIC]
POST   /routes                     Create route            [ADMIN]
```

### Auth
```
POST   /auth/register              Register passenger
POST   /auth/login                 Login → JWT
POST   /auth/refresh               Refresh token
```

### Crew
```
POST   /crew                       Add crew member         [ADMIN]
POST   /flights/{id}/crew          Assign to flight        [STAFF]
GET    /flights/{id}/crew          Crew manifest           [STAFF]
```

All endpoints return a consistent envelope:

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

**Flight Cancellation Cascade**
- Cancelling a flight cascades to all `CONFIRMED` reservations
- Freed seats are returned to inventory
- Events fired for downstream notification handling

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for PostgreSQL via Docker Compose)

### Run Locally

```bash
# Clone the repository
git clone https://github.com/your-org/skyline.git
cd skyline

# Start PostgreSQL
docker compose up -d

# Copy environment template
cp .env.example .env
# Edit .env and set DATABASE_URL, JWT_SECRET

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The API will be available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Environment Variables

| Variable | Description | Example |
|---|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/skyline` |
| `DATABASE_USERNAME` | DB user | `skyline_user` |
| `DATABASE_PASSWORD` | DB password | *(secret)* |
| `JWT_SECRET` | HS256 signing key (min 32 chars) | *(secret)* |
| `JWT_EXPIRY_MS` | Access token TTL in ms | `900000` |
| `JWT_REFRESH_EXPIRY_MS` | Refresh token TTL in ms | `604800000` |

**Never commit `.env` to version control.**

---

## Configuration Profiles

| Profile | Purpose | DB Schema |
|---|---|---|
| `dev` | Local development, SQL logging on | Manual / Flyway off |
| `test` | Automated tests, Testcontainers | Flyway migrations |
| `prod` | Production, all values from env vars | Flyway `validate` only |

---

## Running Tests

```bash
# All tests
./mvnw test

# Unit tests only (no Docker required)
./mvnw test -Dgroups="unit"

# Integration tests (requires Docker for Testcontainers)
./mvnw test -Dgroups="integration"
```

Test coverage targets: **≥ 80%** on service layer, **≥ 60%** overall.

---

## Database Migrations

Flyway migrations live in `src/main/resources/db/migration/`.

```
V1__init.sql
V2__seed_airports.sql
V3__seed_aircraft_types.sql
```

**Never edit an existing migration.** Always create a new versioned file.

---

## SOLID Principles

This project treats SOLID as non-negotiable constraints, not aspirational guidelines.

| Principle | How it's enforced |
|---|---|
| **S** — Single Responsibility | One class, one job. `PricingService` prices. `BoardingPassService` generates passes. Controllers handle HTTP only. |
| **O** — Open/Closed | `PricingStrategy` interface allows new pricing models without modifying existing logic. |
| **L** — Liskov Substitution | All service interfaces are tested independently; implementations must honour the full contract. |
| **I** — Interface Segregation | Read-only and mutation services are split (`FlightSearchService` vs `FlightManagementService`). |
| **D** — Dependency Inversion | All dependencies injected via constructor. No `new` for services or repositories. |

---

## Key Architectural Decisions

**UUID primary keys** — avoids sequential ID enumeration, portable across systems.

**Records for DTOs** — immutable, concise, communicates "this is data, not behaviour."

**Separate Request/Response records** — input and output shapes evolve independently.

**Package-by-feature, not by layer** — `com.skyline.reservation` contains everything about reservations. Horizontal slicing (`com.skyline.service`) creates artificial coupling across unrelated domains.

**`spring.jpa.open-in-view: false`** — prevents Hibernate sessions from leaking through the HTTP layer, avoiding silent performance degradation.

**`@Enumerated(EnumType.STRING)`** — adding enum values won't silently break existing data, unlike `ORDINAL`.

---

## Project Roadmap

**Phase 1 — Core**
- [x] Project scaffolding & security config
- [x] JWT authentication (register, login, refresh)
- [x] Airport & Route CRUD
- [x] Aircraft & Fleet management
- [x] Flight scheduling + status state machine
- [x] Reservation booking + PNR generation
- [x] Flight search endpoint

Phase 2 — Operations *(current focus)*
- [x] Online check-in + boarding pass generation
- [x] Crew assignment + validation
- [ ] Dynamic pricing (load factor)
- [ ] Cancellation cascade

**Phase 3 — Production Readiness**
- [ ] Redis caching for flight search
- [ ] Async notifications (Spring Events / Kafka)
- [ ] Rate limiting on public endpoints
- [ ] Actuator + Prometheus metrics
- [ ] Docker Compose full stack
- [x] GitHub Actions CI pipeline
- [ ] GitHub Actions CD pipeline

---

<div align="center">

*Built with discipline. Designed to scale.*

**SKYLINE** · Airline Management System

</div>