package com.basarsy.skyline.schedule.client.dto;

import java.util.UUID;

public record RouteResponse(
        UUID id,
        AirportSummary origin,
        AirportSummary destination,
        int distanceKm,
        int estimatedDurationMinutes) {
}
