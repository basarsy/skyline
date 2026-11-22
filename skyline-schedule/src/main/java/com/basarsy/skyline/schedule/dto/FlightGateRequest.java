package com.basarsy.skyline.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A departure gate such as {@code B12}, {@code G5} or {@code 214A}. */
public record FlightGateRequest(
        @NotBlank(message = "Gate is required")
        @Pattern(regexp = "[A-Za-z]{0,2}[0-9]{1,3}[A-Za-z]?", message = "Gate must look like B12, G5 or 214A")
        String gate
) {}
