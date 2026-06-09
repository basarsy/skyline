package com.basarsy.skyline.schedule.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FlightRequest(
        @NotBlank(message = "Flight number is required")
        String flightNumber,
        
        @NotNull(message = "Route ID is required")
        UUID routeId,
        
        @NotNull(message = "Aircraft ID is required")
        UUID aircraftId,
        
        @NotNull(message = "Departure time is required")
        @Future(message = "Departure time must be in the future")
        LocalDateTime departureTime,
        
        @NotNull(message = "Arrival time is required")
        @Future(message = "Arrival time must be in the future")
        LocalDateTime arrivalTime,
        
        @NotNull(message = "Base price is required")
        @Positive(message = "Base price must be positive")
        BigDecimal basePrice
) {}
