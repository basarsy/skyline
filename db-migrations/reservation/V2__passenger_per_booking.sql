-- A passenger is a per-booking snapshot (name, passport, ...), not a per-user profile.
-- With UNIQUE(user_id), booking for someone else overwrote the passenger on all of the
-- user's earlier reservations. user_id stays as the booking owner, now non-unique.
ALTER TABLE passengers DROP CONSTRAINT IF EXISTS passengers_user_id_key;
CREATE INDEX IF NOT EXISTS idx_passengers_user ON passengers (user_id);
