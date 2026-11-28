-- The latest known state of each flight, from its change events. Booking events and flight changes come on
-- different topics in no common order: a booking made just before a flight was renumbered, rescheduled or
-- cancelled can be handled after that change, and then takes the flight's details from here instead of the
-- stale ones it carries. changed_at (the change's occurredAt) keeps an older or replayed change from
-- overwriting a newer one.
CREATE TABLE flight_states (
    flight_id       UUID PRIMARY KEY,
    flight_number   VARCHAR(16),
    departure_time  TIMESTAMP,
    cancelled       BOOLEAN NOT NULL,
    changed_at      TIMESTAMP NOT NULL,
    -- The dedupe key of the cancellation notice when the flight is cancelled, exactly as the flight change built
    -- it, so a late booking's passengers who already got it get nothing twice.
    cancellation_key VARCHAR(255)
);
