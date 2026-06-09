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
