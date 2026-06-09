CREATE TABLE passengers (
    id              UUID PRIMARY KEY,
    user_id         UUID UNIQUE,
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
    flight_id    UUID NOT NULL,
    status       VARCHAR(32) NOT NULL,
    seat_number  VARCHAR(8),
    cabin_class  VARCHAR(32) NOT NULL,
    total_price  NUMERIC(12, 2) NOT NULL,
    booked_at    TIMESTAMP NOT NULL,
    pnr          VARCHAR(6) NOT NULL UNIQUE,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    CONSTRAINT uq_reservations_flight_seat UNIQUE (flight_id, seat_number)
);

CREATE INDEX idx_reservations_passenger ON reservations (passenger_id);
CREATE INDEX idx_reservations_flight ON reservations (flight_id);
CREATE INDEX idx_reservations_pnr ON reservations (pnr);
