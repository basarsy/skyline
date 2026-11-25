-- Counts edits of a flight's plan (PUT /flights/{id}). An edit sent with the count it was made from is refused
-- once someone else has edited the flight since, instead of silently overwriting their change. Unlike the row
-- version, bookings (seat counts), delays and gates don't move it, so a busy flight can still be edited.
ALTER TABLE flights ADD COLUMN edit_version INT NOT NULL DEFAULT 0;
