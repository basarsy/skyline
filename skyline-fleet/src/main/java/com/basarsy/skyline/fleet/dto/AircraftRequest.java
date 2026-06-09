package com.basarsy.skyline.fleet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AircraftRequest(
        @NotBlank String tailNumber,
        @NotNull UUID aircraftTypeId,
        @Min(1980) @Max(2100) int manufacturedYear) {}
