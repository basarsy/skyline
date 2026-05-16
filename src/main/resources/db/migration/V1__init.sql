CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE TABLE airports (
    id          UUID PRIMARY KEY,
    iata_code   VARCHAR(3)  NOT NULL UNIQUE,
    icao_code   VARCHAR(4)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    city        VARCHAR(255) NOT NULL,
    country     VARCHAR(255) NOT NULL,
    timezone    VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);

CREATE TABLE aircraft_types (
    id            UUID PRIMARY KEY,
    manufacturer  VARCHAR(128) NOT NULL,
    model         VARCHAR(128) NOT NULL,
    total_seats   INT          NOT NULL,
    cabin_config  TEXT,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE TABLE aircraft (
    id                UUID PRIMARY KEY,
    tail_number       VARCHAR(32) NOT NULL UNIQUE,
    aircraft_type_id  UUID NOT NULL REFERENCES aircraft_types (id),
    status            VARCHAR(32) NOT NULL,
    manufactured_year INT NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE TABLE routes (
    id                         UUID PRIMARY KEY,
    origin_airport_id          UUID NOT NULL REFERENCES airports (id),
    destination_airport_id     UUID NOT NULL REFERENCES airports (id),
    distance_km                INT NOT NULL,
    estimated_duration_minutes INT NOT NULL,
    created_at                 TIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP NOT NULL,
    CONSTRAINT uq_route_origin_dest UNIQUE (origin_airport_id, destination_airport_id)
);

CREATE TABLE flights (
    id               UUID PRIMARY KEY,
    flight_number    VARCHAR(16) NOT NULL,
    route_id         UUID NOT NULL REFERENCES routes (id),
    aircraft_id      UUID NOT NULL REFERENCES aircraft (id),
    departure_time   TIMESTAMP NOT NULL,
    arrival_time     TIMESTAMP NOT NULL,
    status           VARCHAR(32) NOT NULL,
    available_seats  INT NOT NULL,
    base_price       NUMERIC(12, 2) NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT uq_flight_number_departure UNIQUE (flight_number, departure_time)
);

CREATE TABLE passengers (
    id              UUID PRIMARY KEY,
    user_id         UUID UNIQUE REFERENCES users (id),
    first_name      VARCHAR(128) NOT NULL,
    last_name       VARCHAR(128) NOT NULL,
    passport_number VARCHAR(64) NOT NULL,
    date_of_birth   DATE NOT NULL,
    nationality     VARCHAR(64) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE reservations (
    id           UUID PRIMARY KEY,
    passenger_id UUID NOT NULL REFERENCES passengers (id),
    flight_id    UUID NOT NULL REFERENCES flights (id),
    status       VARCHAR(32) NOT NULL,
    seat_number  VARCHAR(8),
    cabin_class  VARCHAR(32) NOT NULL,
    total_price  NUMERIC(12, 2) NOT NULL,
    booked_at    TIMESTAMP NOT NULL,
    pnr          VARCHAR(6) NOT NULL UNIQUE,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

CREATE TABLE boarding_passes (
    id              UUID PRIMARY KEY,
    reservation_id  UUID NOT NULL UNIQUE REFERENCES reservations (id),
    gate            VARCHAR(16) NOT NULL,
    boarding_time   TIMESTAMP NOT NULL,
    seat_number     VARCHAR(8) NOT NULL,
    barcode_data    VARCHAR(512) NOT NULL,
    issued_at       TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE crew_members (
    id                  UUID PRIMARY KEY,
    user_id             UUID UNIQUE REFERENCES users (id),
    employee_number     VARCHAR(32) NOT NULL UNIQUE,
    role                VARCHAR(32) NOT NULL,
    license_number      VARCHAR(64) NOT NULL,
    license_expiry_date DATE NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE TABLE flight_crew (
    id              UUID PRIMARY KEY,
    flight_id       UUID NOT NULL REFERENCES flights (id),
    crew_member_id  UUID NOT NULL REFERENCES crew_members (id),
    role            VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_flight_crew_member UNIQUE (flight_id, crew_member_id)
);

CREATE INDEX idx_flights_route ON flights (route_id);
CREATE INDEX idx_flights_departure ON flights (departure_time);
CREATE INDEX idx_reservations_passenger ON reservations (passenger_id);
CREATE INDEX idx_reservations_flight ON reservations (flight_id);
CREATE INDEX idx_reservations_pnr ON reservations (pnr);
