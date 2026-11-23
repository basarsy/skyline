-- One row per seat a booking holds on a flight, keyed by an id the reservation service chooses. Claiming the same
-- id twice takes one seat, and releasing a claim that never landed gives none back, so a call whose response was
-- lost can be retried or compensated safely. flight_id and created_at let a reconciliation find orphan claims.
CREATE TABLE seat_claims (
    id          UUID PRIMARY KEY,
    flight_id   UUID NOT NULL REFERENCES flights (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_seat_claims_flight ON seat_claims (flight_id);
