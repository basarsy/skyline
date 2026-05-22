package com.basarsy.skyline.schedule.dto;

import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FlightResponse(
        UUID id,
        String flightNumber,
        RouteResponse route,
        AircraftResponse aircraft,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        FlightStatus status,
        int availableSeats,
        BigDecimal basePrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
