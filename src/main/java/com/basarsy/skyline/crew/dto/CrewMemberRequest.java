package com.basarsy.skyline.crew.dto;

import com.basarsy.skyline.crew.entity.CrewRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CrewMemberRequest(
    @NotNull UUID userId,
    @NotBlank String employeeNumber,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull CrewRole role,
    @NotBlank String licenseNumber,
    @NotNull LocalDate licenseExpiryDate
) {}
