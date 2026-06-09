package com.basarsy.skyline.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
    @NotBlank(message = "PNR is required")
    @Size(min = 6, max = 6, message = "PNR must be 6 characters")
    String pnr
) {}
