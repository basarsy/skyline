package com.basarsy.skyline.crew.dto;

import com.basarsy.skyline.crew.entity.CrewRole;
import java.util.UUID;

public record FlightCrewResponse(
    UUID crewMemberId,
    String employeeNumber,
    String firstName,
    String lastName,
    CrewRole assignedRole
) {}
