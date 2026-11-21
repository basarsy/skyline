-- Transactional outbox: an event is written in the same transaction as the change it describes and
-- published to Kafka afterwards by OutboxPublisher. Timestamps are set and compared in SQL.
CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    -- Insertion order. Rows written in one transaction share created_at (now() is the transaction start), so
    -- per-aggregate ordering uses this instead.
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

-- The publisher claims unpublished rows that are due, oldest first...
CREATE INDEX idx_outbox_events_pending ON outbox_events (next_attempt_at, seq) WHERE published_at IS NULL;
-- ...skipping any row whose aggregate still has an older unpublished one, so events are sent in order per key.
CREATE INDEX idx_outbox_events_pending_aggregate ON outbox_events (aggregate_id, seq) WHERE published_at IS NULL;
-- Retention cleanup deletes published rows by age.
CREATE INDEX idx_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
