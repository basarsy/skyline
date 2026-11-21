-- Flight search filters by route and a departure-time range: one composite index serves both.
-- It also covers route-only lookups (leading column), so the single-column route index is redundant.
CREATE INDEX idx_flights_route_departure ON flights (route_id, departure_time);
DROP INDEX idx_flights_route;
