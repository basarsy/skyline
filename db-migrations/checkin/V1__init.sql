CREATE TABLE boarding_passes (
    id              UUID PRIMARY KEY,
    reservation_id  UUID NOT NULL UNIQUE,
    gate            VARCHAR(16) NOT NULL,
    boarding_time   TIMESTAMP NOT NULL,
    seat_number     VARCHAR(8) NOT NULL,
    barcode_data    VARCHAR(512) NOT NULL,
    issued_at       TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);
