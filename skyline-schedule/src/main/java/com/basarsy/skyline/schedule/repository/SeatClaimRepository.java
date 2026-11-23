package com.basarsy.skyline.schedule.repository;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code seat_claims}: which booking holds a seat on which flight (see V5__seat_claims.sql). */
@Repository
@RequiredArgsConstructor
public class SeatClaimRepository {

    private final JdbcTemplate jdbc;

    /** Records the claim; false when it already exists (on this flight or another). */
    public boolean insert(UUID claimId, UUID flightId) {
        return jdbc.update("INSERT INTO seat_claims (id, flight_id) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                claimId, flightId) == 1;
    }

    public Optional<UUID> flightOf(UUID claimId) {
        return jdbc.query("SELECT flight_id FROM seat_claims WHERE id = ?",
                (rs, row) -> rs.getObject("flight_id", UUID.class), claimId).stream().findFirst();
    }

    /** Removes the claim; false when there was none on that flight (never landed, or already released). */
    public boolean delete(UUID claimId, UUID flightId) {
        return jdbc.update("DELETE FROM seat_claims WHERE id = ? AND flight_id = ?", claimId, flightId) == 1;
    }

    /** A cancelled flight's seats are reset to capacity: its claims are void, so releasing one gives nothing back. */
    public void deleteForFlight(UUID flightId) {
        jdbc.update("DELETE FROM seat_claims WHERE flight_id = ?", flightId);
    }
}
