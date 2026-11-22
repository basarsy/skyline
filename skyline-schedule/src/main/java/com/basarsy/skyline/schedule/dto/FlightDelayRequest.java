package com.basarsy.skyline.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** The departure time ops now expect (UTC). Must be after the scheduled departure, by at most a day. */
public record FlightDelayRequest(
        @NotNull(message = "Estimated departure time is required")
        LocalDateTime estimatedDepartureTime
) {}
