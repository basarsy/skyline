package com.basarsy.skyline.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** A seat for an existing reservation (hold it, or confirm the held seat). */
public record SeatChoiceRequest(
        @NotNull @Pattern(regexp = "\\d{1,2}[A-Z]", message = "seatNumber must look like 12A") String seatNumber) {}
