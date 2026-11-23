-- The id under which the schedule service holds this booking's seat (its seat_claims). Giving the seat back by
-- that id is safe to repeat. Null on bookings made before claims existed: those give it back with a plain increment.
ALTER TABLE reservations ADD COLUMN seat_claim_id UUID;
