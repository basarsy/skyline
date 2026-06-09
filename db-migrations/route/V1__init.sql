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
