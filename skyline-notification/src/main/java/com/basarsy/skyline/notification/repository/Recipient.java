package com.basarsy.skyline.notification.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/** A passenger booked on a flight, and where to reach them. Departure is the scheduled time, UTC. */
public record Recipient(
        UUID reservationId,
        UUID bookingId,
        UUID flightId,
        UUID userId,
        String contactEmail,
        String passengerName,
        String pnr,
        String flightNumber,
        String origin,
        String destination,
        String originTimezone,
        LocalDateTime departureTime) {}
