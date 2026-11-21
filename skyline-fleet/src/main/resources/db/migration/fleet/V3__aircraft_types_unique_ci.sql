-- One aircraft type per manufacturer + model, regardless of case ("Airbus A320neo" = "airbus a320NEO").
-- The service checks this too; the index closes the race between two concurrent creates.
CREATE UNIQUE INDEX uq_aircraft_types_manufacturer_model_ci ON aircraft_types (LOWER(manufacturer), LOWER(model));
