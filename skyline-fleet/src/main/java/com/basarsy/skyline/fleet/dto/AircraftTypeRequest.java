package com.basarsy.skyline.fleet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AircraftTypeRequest(
        @NotBlank String manufacturer,
        @NotBlank String model,
        @Min(1) int totalSeats,
        String cabinConfig) {}
