CREATE TABLE crew_members (
    id                  UUID PRIMARY KEY,
    user_id             UUID UNIQUE,
    employee_number     VARCHAR(32) NOT NULL UNIQUE,
    role                VARCHAR(32) NOT NULL,
    license_number      VARCHAR(64) NOT NULL,
    license_expiry_date DATE NOT NULL,
    first_name          VARCHAR(128) NOT NULL,
    last_name           VARCHAR(128) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE TABLE flight_crew (
    id              UUID PRIMARY KEY,
    flight_id       UUID NOT NULL,
    crew_member_id  UUID NOT NULL REFERENCES crew_members (id),
    role            VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_flight_crew_member UNIQUE (flight_id, crew_member_id)
);
