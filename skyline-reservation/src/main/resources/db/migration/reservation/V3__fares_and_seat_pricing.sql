-- Fare types within a cabin, and booking groups (several passengers and/or legs booked together).
-- Every existing booking is a STANDARD fare on its own; its fare price is what it cost so far.
ALTER TABLE reservations ADD COLUMN fare_type VARCHAR(16) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE reservations ADD COLUMN fare_price NUMERIC(12, 2);
UPDATE reservations SET fare_price = total_price;
ALTER TABLE reservations ALTER COLUMN fare_price SET NOT NULL;
ALTER TABLE reservations ADD COLUMN booking_group_id UUID;
CREATE INDEX idx_reservations_booking_group ON reservations (booking_group_id);

-- Seat prices by cabin, fare and seat category (an admin edits these). Zero = included in the fare.
CREATE TABLE seat_price_rules (
    id            UUID PRIMARY KEY,
    cabin_class   VARCHAR(32) NOT NULL,
    fare_type     VARCHAR(16) NOT NULL,
    seat_category VARCHAR(32) NOT NULL,
    price         NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    auto_assign   BOOLEAN NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_seat_price_rule UNIQUE (cabin_class, fare_type, seat_category)
);

-- Economy: BASIC pays for every seat, STANDARD gets ordinary seats free, FLEX pays only for extra legroom.
-- Check-in only hands out STANDARD and PREFERRED seats for free; the paid premium seats stay sellable.
INSERT INTO seat_price_rules (id, cabin_class, fare_type, seat_category, price, auto_assign, created_at, updated_at)
SELECT gen_random_uuid(), 'ECONOMY', fare, category, price, category IN ('STANDARD', 'PREFERRED'), NOW(), NOW()
FROM (VALUES
    ('BASIC', 'STANDARD', 8.00), ('BASIC', 'PREFERRED', 12.00), ('BASIC', 'FRONT', 18.00),
    ('BASIC', 'EXTRA_LEGROOM', 28.00), ('BASIC', 'EXIT_ROW', 32.00),
    ('STANDARD', 'STANDARD', 0.00), ('STANDARD', 'PREFERRED', 6.00), ('STANDARD', 'FRONT', 12.00),
    ('STANDARD', 'EXTRA_LEGROOM', 22.00), ('STANDARD', 'EXIT_ROW', 25.00),
    ('FLEX', 'STANDARD', 0.00), ('FLEX', 'PREFERRED', 0.00), ('FLEX', 'FRONT', 0.00),
    ('FLEX', 'EXTRA_LEGROOM', 12.00), ('FLEX', 'EXIT_ROW', 15.00)
) AS seed (fare, category, price);

-- Business and First include seat selection on every fare, and any seat of the cabin can be assigned.
INSERT INTO seat_price_rules (id, cabin_class, fare_type, seat_category, price, auto_assign, created_at, updated_at)
SELECT gen_random_uuid(), cabin, fare, category, 0.00, TRUE, NOW(), NOW()
FROM (VALUES ('BUSINESS'), ('FIRST')) AS c (cabin)
CROSS JOIN (VALUES ('BASIC'), ('STANDARD'), ('FLEX')) AS f (fare)
CROSS JOIN (VALUES ('STANDARD'), ('PREFERRED'), ('FRONT'), ('EXTRA_LEGROOM'), ('EXIT_ROW')) AS s (category);

-- Seats taken out of sale on one flight (broken seat, crew rest, weight and balance), set by staff.
CREATE TABLE blocked_seats (
    id          UUID PRIMARY KEY,
    flight_id   UUID NOT NULL,
    seat_number VARCHAR(8) NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uq_blocked_seat UNIQUE (flight_id, seat_number)
);
