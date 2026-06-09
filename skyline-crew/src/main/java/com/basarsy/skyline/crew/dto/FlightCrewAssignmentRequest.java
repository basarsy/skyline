package com.basarsy.skyline.crew.dto;

import com.basarsy.skyline.crew.entity.CrewRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FlightCrewAssignmentRequest(
    @NotNull UUID crewMemberId,
    @NotNull CrewRole role
) {}
