package com.basarsy.skyline.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An admin's edit of a catalog product. The code and type are fixed; prices have their own endpoint. */
public record AncillaryProductRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 512) String description,
        @Min(1) @Max(10) int maxQuantity,
        @Min(0) @Max(168) int cutoffHours,
        @NotNull Boolean alwaysRefundable,
        @NotNull Boolean active) {}
