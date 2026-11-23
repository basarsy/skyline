-- One row per seat a booking holds on a flight, keyed by an id the reservation service chooses. Claiming the same
-- id twice takes one seat, and releasing a claim that never landed gives none back, so a call whose response was
-- lost can be retried or compensated safely. A released claim keeps its row (released_at): a claim call that
-- arrives after its own release (it was slow, the release was not) is refused instead of taking a seat no one
-- will give back. flight_id and created_at let a reconciliation find orphan claims.
CREATE TABLE seat_claims (
    id           UUID PRIMARY KEY,
    flight_id    UUID NOT NULL REFERENCES flights (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at  TIMESTAMPTZ
);
CREATE INDEX idx_seat_claims_flight ON seat_claims (flight_id) WHERE released_at IS NULL;
