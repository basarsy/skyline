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

/** {@code flight_states}: the latest known state of each flight (see V2__flight_states.sql and V3). */
@Repository
@RequiredArgsConstructor
public class FlightStateRepository {

    /**
     * A flight as its latest change left it. {@code changedAt} is when that change happened (UTC);
     * {@code cancellationKey} the dedupe key of its cancellation notice, null unless it is cancelled;
     * {@code estimatedDepartureTime} the expected departure while delayed, else null.
     */
    public record FlightState(String flightNumber, LocalDateTime departureTime, boolean cancelled,
            LocalDateTime changedAt, String cancellationKey, LocalDateTime estimatedDepartureTime, String gate) {}

    private final JdbcTemplate jdbc;

    /**
     * Records the flight as a change left it, unless an earlier-dated change is replayed over a later one.
     * Changes of one flight arrive in order (one Kafka key), so of two with the same time the later one wins.
     */
    public void record(UUID flightId, FlightState state) {
        jdbc.update("""
                INSERT INTO flight_states (flight_id, flight_number, departure_time, cancelled, changed_at,
                    cancellation_key, estimated_departure_time, gate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (flight_id) DO UPDATE SET flight_number = EXCLUDED.flight_number,
                    departure_time = EXCLUDED.departure_time, cancelled = EXCLUDED.cancelled,
                    changed_at = EXCLUDED.changed_at,
                    cancellation_key = COALESCE(EXCLUDED.cancellation_key, flight_states.cancellation_key),
                    estimated_departure_time = EXCLUDED.estimated_departure_time,
                    gate = EXCLUDED.gate
                WHERE flight_states.changed_at <= EXCLUDED.changed_at""",
                flightId, state.flightNumber(), timestamp(state.departureTime()), state.cancelled(),
                timestamp(state.changedAt()), state.cancellationKey(), timestamp(state.estimatedDepartureTime()),
                state.gate());
    }

    /**
     * Serialises a flight's change and the bookings of it until the transaction ends. A change records the state
     * and then tells the flight's recipients; a booking reads the state and then becomes a recipient. Without
     * the lock the two can run at once and each miss the other, so the passenger hears of neither. Taken in id
     * order, so two bookings sharing flights can't deadlock.
     */
    public void lock(Collection<UUID> flightIds) {
        flightIds.stream().distinct().sorted().forEach(id -> jdbc.query("SELECT pg_advisory_xact_lock(?)",
                rs -> null, id.getMostSignificantBits() ^ id.getLeastSignificantBits()));
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
            states.put(rs.getObject("flight_id", UUID.class), new FlightState(rs.getString("flight_number"),
                    localDateTime(rs.getTimestamp("departure_time")), rs.getBoolean("cancelled"),
                    rs.getTimestamp("changed_at").toLocalDateTime(), rs.getString("cancellation_key"),
                    localDateTime(rs.getTimestamp("estimated_departure_time")), rs.getString("gate")));
        });
        return states;
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
