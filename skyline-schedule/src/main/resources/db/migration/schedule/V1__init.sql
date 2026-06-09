CREATE TABLE flights (
    id               UUID PRIMARY KEY,
    flight_number    VARCHAR(16) NOT NULL,
    route_id         UUID NOT NULL,
    aircraft_id      UUID NOT NULL,
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

CREATE INDEX idx_flights_route ON flights (route_id);
CREATE INDEX idx_flights_departure ON flights (departure_time);
