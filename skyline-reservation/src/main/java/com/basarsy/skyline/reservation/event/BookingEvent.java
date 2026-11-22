package com.basarsy.skyline.reservation.event;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A booking was made or a reservation cancelled ({@code skyline.booking.events}). Keyed by {@code bookingId}, so
 * a booking's events stay in order. Records carry no type headers: {@code type} says what happened. Each event is
 * self-contained (flight, route and passengers as booked), so consumers such as notifications or loyalty never
 * call back. Times are UTC.
 *
 * @param bookingId    the booking group, or the reservation itself for a single booking
 * @param userId       the account that booked
 * @param contactEmail where messages about the booking go; null on bookings made before it was recorded
 * @param reservations BOOKED: every reservation of the booking; CANCELLED: the cancelled one
 */
public record BookingEvent(
        BookingEventType type,
        UUID bookingId,
        UUID userId,
        String contactEmail,
        List<Item> reservations,
        LocalDateTime occurredAt) {

    /** One passenger on one flight. */
    public record Item(
            UUID reservationId,
            String pnr,
            String passengerName,
            UUID flightId,
            String flightNumber,
            String origin,
            String destination,
            String originTimezone,
            String destinationTimezone,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            Integer distanceKm,
            CabinClass cabinClass,
            FareType fareType,
            BigDecimal totalPrice,
            String currency) {}
}
