package com.basarsy.skyline.crew.dto;

import com.basarsy.skyline.crew.entity.CrewRole;
import java.time.LocalDate;
import java.util.UUID;

public record CrewMemberResponse(
    UUID id,
    UUID userId,
    String employeeNumber,
    String firstName,
    String lastName,
    CrewRole role,
    String licenseNumber,
    LocalDate licenseExpiryDate
) {}
