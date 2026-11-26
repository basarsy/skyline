package com.basarsy.skyline.notification.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code flight_states}: the latest known state of each flight (see V2__flight_states.sql). */
@Repository
@RequiredArgsConstructor
public class FlightStateRepository {

    /** A flight as its latest change left it; {@code changedAt} is when that change happened (UTC). */
    public record FlightState(String flightNumber, LocalDateTime departureTime, boolean cancelled,
            LocalDateTime changedAt) {}

    private final JdbcTemplate jdbc;

    /** Records the flight as a change left it, unless a later change is recorded already. */
    public void record(UUID flightId, FlightState state) {
        jdbc.update("""
                INSERT INTO flight_states (flight_id, flight_number, departure_time, cancelled, changed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (flight_id) DO UPDATE SET flight_number = EXCLUDED.flight_number,
                    departure_time = EXCLUDED.departure_time, cancelled = EXCLUDED.cancelled,
                    changed_at = EXCLUDED.changed_at
                WHERE flight_states.changed_at < EXCLUDED.changed_at""",
                flightId, state.flightNumber(), timestamp(state.departureTime()), state.cancelled(),
                timestamp(state.changedAt()));
    }

    /** The recorded state of each of {@code flightIds} that has one. */
    public Map<UUID, FlightState> find(Collection<UUID> flightIds) {
        Map<UUID, FlightState> states = new HashMap<>();
        if (flightIds.isEmpty()) {
            return states;
        }
        jdbc.query(con -> {
            var statement = con.prepareStatement("SELECT * FROM flight_states WHERE flight_id = ANY(?)");
            statement.setArray(1, con.createArrayOf("uuid", flightIds.toArray()));
            return statement;
        }, rs -> {
            Timestamp departure = rs.getTimestamp("departure_time");
            states.put(rs.getObject("flight_id", UUID.class), new FlightState(rs.getString("flight_number"),
                    departure == null ? null : departure.toLocalDateTime(), rs.getBoolean("cancelled"),
                    rs.getTimestamp("changed_at").toLocalDateTime()));
        });
        return states;
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
