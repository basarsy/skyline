package com.basarsy.skyline.notification.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A {@code skyline.flight.changed} record (the schedule service's FlightChangedEvent). {@code type} and the
 * statuses are kept as text, so a value added in the schedule service is ignored here instead of failing the
 * record. Times are UTC.
 */
public record FlightChange(
        String type,
        UUID flightId,
        String flightNumber,
        String origin,
        String destination,
        String originTimezone,
        String destinationTimezone,
        String status,
        String previousStatus,
        LocalDateTime departureTime,
        LocalDateTime previousDepartureTime,
        LocalDateTime arrivalTime,
        LocalDateTime estimatedDepartureTime,
        LocalDateTime previousEstimatedDepartureTime,
        String gate,
        String previousGate,
        LocalDateTime occurredAt) {

    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String DELAYED = "DELAYED";
    public static final String DELAY_CLEARED = "DELAY_CLEARED";
    public static final String RESCHEDULED = "RESCHEDULED";
    public static final String GATE_CHANGED = "GATE_CHANGED";
    public static final String CANCELLED = "CANCELLED";

    /** When the flight is now expected to leave: the delayed time, else the scheduled one. */
    public LocalDateTime expectedDeparture() {
        return estimatedDepartureTime != null ? estimatedDepartureTime : departureTime;
    }
}
