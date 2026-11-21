-- Every paid extra on a reservation is one line item: the seat fee now, bags and other add-ons later. The
-- reservation's total_price is its fare_price plus the ACTIVE items.
CREATE TABLE reservation_ancillaries (
    id             UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations (id),
    type           VARCHAR(32) NOT NULL,
    product_code   VARCHAR(64) NOT NULL,
    description    VARCHAR(255) NOT NULL,
    seat_number    VARCHAR(8),
    quantity       INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    price          NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    channel        VARCHAR(16) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    refundable     BOOLEAN NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL
);

CREATE INDEX idx_reservation_ancillaries_reservation ON reservation_ancillaries (reservation_id);

-- One seat per passenger per flight: at most one active seat line per reservation.
CREATE UNIQUE INDEX uq_reservation_active_seat ON reservation_ancillaries (reservation_id)
    WHERE type = 'SEAT' AND status = 'ACTIVE';
