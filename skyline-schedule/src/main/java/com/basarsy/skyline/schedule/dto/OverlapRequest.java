package com.basarsy.skyline.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Which of {@code flightIds} occupy their crew while {@code flightId} flies (at its expected times, read here at
 * check time). The crew service asks it for a crew member's other flights, at most {@link #MAX_IDS} per call.
 */
public record OverlapRequest(
        @NotNull UUID flightId,
        @NotNull @Size(max = MAX_IDS) List<UUID> flightIds) {

    public static final int MAX_IDS = 1000;
}
