package com.basarsy.skyline.schedule.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code seat_claims}: which booking holds a seat on which flight (see V5__seat_claims.sql). */
@Repository
@RequiredArgsConstructor
public class SeatClaimRepository {

    /** A claim id seen before: where it is, and whether it was released. */
    public record Claim(UUID flightId, boolean released) {}

    private final JdbcTemplate jdbc;

    /**
     * Records the claim; false when the id is known already (held, released, or on another flight). A concurrent
     * insert or release of the same id waits for the other transaction, so the two never both win.
     */
    public boolean insert(UUID claimId, UUID flightId) {
        return jdbc.update("INSERT INTO seat_claims (id, flight_id) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                claimId, flightId) == 1;
    }

    public Optional<Claim> find(UUID claimId) {
        return jdbc.query("SELECT flight_id, released_at FROM seat_claims WHERE id = ?",
                (rs, row) -> new Claim(rs.getObject("flight_id", UUID.class), rs.getObject("released_at") != null),
                claimId).stream().findFirst();
    }

    /**
     * Marks the claim released; true only when it held a seat on that flight until now (so the seat goes back).
     * A claim not seen yet is recorded as released, so if its claim call is still on its way it will be refused.
     * Upsert: inserted (xmax = 0) is that marker, updated (xmax &lt;&gt; 0) was a held claim; an already
     * released claim, or one on another flight, matches neither and returns nothing.
     */
    public boolean release(UUID claimId, UUID flightId) {
        List<Boolean> held = jdbc.query("""
                INSERT INTO seat_claims (id, flight_id, released_at) VALUES (?, ?, now())
                ON CONFLICT (id) DO UPDATE SET released_at = now()
                    WHERE seat_claims.released_at IS NULL AND seat_claims.flight_id = EXCLUDED.flight_id
                RETURNING xmax <> 0 AS held""",
                (rs, row) -> rs.getBoolean("held"), claimId, flightId);
        return !held.isEmpty() && held.get(0);
    }

    /** A cancelled flight's seats are reset to capacity: its claims are void, so releasing one gives nothing back. */
    public void releaseAllOnFlight(UUID flightId) {
        jdbc.update("UPDATE seat_claims SET released_at = now() WHERE flight_id = ? AND released_at IS NULL", flightId);
    }
}
