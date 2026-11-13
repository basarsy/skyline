package com.basarsy.skyline.schedule.dto;

import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filters of the operations flight board. Every field is optional; null (or an empty status set) means "any".
 *
 * @param date         departure day, a UTC calendar day
 * @param statuses     any of these statuses
 * @param flightNumber case-insensitive "contains" match
 * @param routeId      exact route
 */
public record FlightBoardFilter(LocalDate date, Set<FlightStatus> statuses, String flightNumber, UUID routeId) {
}
