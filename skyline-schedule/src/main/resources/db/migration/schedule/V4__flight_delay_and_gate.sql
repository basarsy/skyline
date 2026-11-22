-- Operations data on a flight: the departure time ops now expect (null while on time) and the departure gate
-- (null until one is assigned). Search, the check-in window and add-on cut-offs keep using departure_time.
ALTER TABLE flights ADD COLUMN estimated_departure_time TIMESTAMP;
ALTER TABLE flights ADD COLUMN gate VARCHAR(8);

ALTER TABLE flights ADD CONSTRAINT chk_flights_estimate_after_departure
    CHECK (estimated_departure_time IS NULL OR estimated_departure_time > departure_time);
