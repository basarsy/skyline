package com.basarsy.skyline.route.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RouteRequest(
        @NotNull UUID originAirportId,
        @NotNull UUID destinationAirportId,
        @Min(1) int distanceKm,
        @Min(1) int estimatedDurationMinutes) {
}
