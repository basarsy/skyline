package com.basarsy.skyline.notification.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A {@code skyline.booking.events} record (the reservation service's BookingEvent): {@code BOOKED} with every
 * reservation of a booking, or {@code CANCELLED} with the one a passenger cancelled. Times are UTC.
 */
public record BookingEvent(
        String type,
        UUID bookingId,
        UUID userId,
        String contactEmail,
        List<Item> reservations,
        LocalDateTime occurredAt) {

    public static final String BOOKED = "BOOKED";
    public static final String CANCELLED = "CANCELLED";

    public List<Item> reservations() {
        return reservations == null ? List.of() : reservations;
    }

    /** One passenger on one flight; only what messages need of it. */
    public record Item(
            UUID reservationId,
            String pnr,
            String passengerName,
            UUID flightId,
            String flightNumber,
            String origin,
            String destination,
            String originTimezone,
            LocalDateTime departureTime) {}
}
