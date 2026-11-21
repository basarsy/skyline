-- Overlap checks list a crew member's assignments. The (flight_id, crew_member_id) unique index
-- can't serve a lookup by crew member alone, so give it its own index.
CREATE INDEX idx_flight_crew_crew_member ON flight_crew (crew_member_id);
