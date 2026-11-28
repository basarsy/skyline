-- A flight's current delay (expected departure, null while on time) and gate, from its changes (each carries
-- them). A booking handled after them (see V2__flight_states.sql) shows them in its confirmation: its passengers
-- weren't recipients yet when the delay or gate notice went out.
ALTER TABLE flight_states ADD COLUMN estimated_departure_time TIMESTAMP;
ALTER TABLE flight_states ADD COLUMN gate VARCHAR(8);
