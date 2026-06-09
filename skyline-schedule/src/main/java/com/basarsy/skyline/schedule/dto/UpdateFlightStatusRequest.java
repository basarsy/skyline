package com.basarsy.skyline.schedule.dto;

import com.basarsy.skyline.schedule.entity.FlightStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFlightStatusRequest(
        @NotNull(message = "Status is required")
        FlightStatus status
) {}
