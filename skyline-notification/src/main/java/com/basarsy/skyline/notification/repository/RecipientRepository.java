package com.basarsy.skyline.notification.repository;

import com.basarsy.skyline.notification.event.BookingEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code booking_recipients}: who travels on which flight, kept from the booking events. */
@Repository
@RequiredArgsConstructor
public class RecipientRepository {

    private final JdbcTemplate jdbc;

    /** A booked reservation. A redelivered event, or one that arrives after its cancellation, changes nothing. */
    public void booked(UUID bookingId, UUID userId, String contactEmail, BookingEvent.Item item) {
        jdbc.update("""
                INSERT INTO booking_recipients (reservation_id, booking_id, flight_id, user_id, contact_email,
                    passenger_name, pnr, flight_number, origin, destination, origin_timezone, departure_time, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                ON CONFLICT (reservation_id) DO NOTHING""",
                item.reservationId(), bookingId, item.flightId(), userId, contactEmail, item.passengerName(),
                item.pnr(), item.flightNumber(), item.origin(), item.destination(), item.originTimezone(),
                timestamp(item.departureTime()));
    }

    /** A cancelled reservation: inactive from now on, also when its BOOKED event has not been seen yet. */
    public void cancelled(UUID bookingId, UUID userId, String contactEmail, BookingEvent.Item item) {
        jdbc.update("""
                INSERT INTO booking_recipients (reservation_id, booking_id, flight_id, user_id, contact_email,
                    passenger_name, pnr, flight_number, origin, destination, origin_timezone, departure_time, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                ON CONFLICT (reservation_id) DO UPDATE SET active = FALSE, updated_at = now()""",
                item.reservationId(), bookingId, item.flightId(), userId, contactEmail, item.passengerName(),
                item.pnr(), item.flightNumber(), item.origin(), item.destination(), item.originTimezone(),
                timestamp(item.departureTime()));
    }

    /** Everyone still booked on a flight, in booking order. */
    public List<Recipient> activeOnFlight(UUID flightId) {
        return jdbc.query("SELECT * FROM booking_recipients WHERE flight_id = ? AND active ORDER BY created_at, pnr",
                RecipientRepository::map, flightId);
    }

    /** The airline cancelled the flight: nobody is booked on it any more. */
    public void deactivateFlight(UUID flightId) {
        jdbc.update("UPDATE booking_recipients SET active = FALSE, updated_at = now() WHERE flight_id = ? AND active",
                flightId);
    }

    /**
     * The flight's scheduled departure moved (check-in reminders follow it). With {@code rearmReminder} (the new
     * check-in window opens after the move, so check-in is closed again), a reminder already sent goes out again.
     */
    public void rescheduleFlight(UUID flightId, LocalDateTime departureTime, boolean rearmReminder) {
        jdbc.update("""
                UPDATE booking_recipients
                SET departure_time = ?,
                    check_in_reminded_at = CASE WHEN ? THEN NULL ELSE check_in_reminded_at END,
                    updated_at = now()
                WHERE flight_id = ?""",
                timestamp(departureTime), rearmReminder, flightId);
    }

    /**
     * Locks up to {@code limit} active bookings whose check-in window ({@code opensBefore} → {@code closesBefore}
     * before departure) is open at {@code now} and that were not reminded yet, until the transaction ends.
     */
    public List<Recipient> claimDueReminders(LocalDateTime now, java.time.Duration opensBefore,
            java.time.Duration closesBefore, int limit) {
        return jdbc.query("""
                SELECT * FROM booking_recipients
                WHERE active AND check_in_reminded_at IS NULL
                  AND departure_time <= ? AND departure_time > ?
                ORDER BY departure_time
                LIMIT ?
                FOR UPDATE SKIP LOCKED""",
                RecipientRepository::map, timestamp(now.plus(opensBefore)), timestamp(now.plus(closesBefore)), limit);
    }

    public void markReminded(List<UUID> reservationIds) {
        if (reservationIds.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            var statement = con.prepareStatement(
                    "UPDATE booking_recipients SET check_in_reminded_at = now(), updated_at = now() WHERE reservation_id = ANY(?)");
            statement.setArray(1, con.createArrayOf("uuid", reservationIds.toArray()));
            return statement;
        });
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Recipient map(ResultSet rs, int row) throws SQLException {
        Timestamp departure = rs.getTimestamp("departure_time");
        return new Recipient(
                rs.getObject("reservation_id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("flight_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("contact_email"),
                rs.getString("passenger_name"),
                rs.getString("pnr"),
                rs.getString("flight_number"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getString("origin_timezone"),
                departure == null ? null : departure.toLocalDateTime());
    }
}
