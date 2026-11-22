package com.basarsy.skyline.schedule.event;

import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Something a passenger should hear about happened to a flight ({@code skyline.flight.changed}, keyed by flight
 * id). Records carry no type headers, so {@code type} says what changed. Each event is self-contained: it holds
 * the flight as it now is plus the previous value of what changed, so a consumer can describe it (an email, a
 * banner) without calling the schedule service back. Times are UTC.
 *
 * @param type                         what changed
 * @param origin                       origin IATA code, null if the route service was unreachable
 * @param destination                  destination IATA code, null if the route service was unreachable
 * @param departureTime                scheduled departure (after a reschedule, the new one)
 * @param estimatedDepartureTime       expected departure while delayed, else null
 * @param previousDepartureTime        scheduled departure before a reschedule, else equal to {@code departureTime}
 * @param previousEstimatedDepartureTime expected departure before this change, else null
 * @param previousStatus               status before this change
 * @param previousGate                 gate before this change, null if none was assigned
 */
public record FlightChangedEvent(
        FlightChangeType type,
        UUID flightId,
        String flightNumber,
        String origin,
        String destination,
        FlightStatus status,
        FlightStatus previousStatus,
        LocalDateTime departureTime,
        LocalDateTime previousDepartureTime,
        LocalDateTime arrivalTime,
        LocalDateTime estimatedDepartureTime,
        LocalDateTime previousEstimatedDepartureTime,
        String gate,
        String previousGate,
        LocalDateTime occurredAt) {}
