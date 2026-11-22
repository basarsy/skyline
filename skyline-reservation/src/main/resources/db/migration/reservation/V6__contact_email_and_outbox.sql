-- Where messages about a booking go (confirmation, delays, gate changes). Null on bookings made before it existed.
ALTER TABLE reservations ADD COLUMN contact_email VARCHAR(254);

-- Transactional outbox for booking events, the same table as the schedule service's (V3__outbox_events.sql there):
-- an event is written in the transaction of the booking change and published to Kafka afterwards.
CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    -- Insertion order; rows of one transaction share created_at, so per-aggregate ordering uses this.
    seq              BIGINT GENERATED ALWAYS AS IDENTITY,
    aggregate_id     UUID NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    topic            VARCHAR(255) NOT NULL,
    payload          TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ,
    attempts         INT NOT NULL DEFAULT 0,
    last_error       TEXT
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (next_attempt_at, seq) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_events_pending_aggregate ON outbox_events (aggregate_id, seq) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
