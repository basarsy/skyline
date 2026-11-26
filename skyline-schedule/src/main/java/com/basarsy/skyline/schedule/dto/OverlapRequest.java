package com.basarsy.skyline.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Which of {@code flightIds} occupy their crew between {@code from} and {@code until} (UTC): the crew service
 * asks it once for all of a crew member's other flights instead of fetching them one by one.
 */
public record OverlapRequest(
        @NotNull @Size(max = 10_000) List<UUID> flightIds,
        @NotNull LocalDateTime from,
        @NotNull LocalDateTime until) {}
