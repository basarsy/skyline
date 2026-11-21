package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Hold a seat while booking, before the reservation exists. The cabin and fare are what is being bought, so the
 * hold is priced (and the cabin checked) on the server.
 *
 * @param passengerIndex the passenger's position in the booking, from 0
 */
public record SeatHoldRequest(
        @NotNull UUID flightId,
        @NotNull @Pattern(regexp = "\\d{1,2}[A-Z]", message = "seatNumber must look like 12A") String seatNumber,
        @Min(0) @Max(8) int passengerIndex,
        @NotNull CabinClass cabinClass,
        FareType fareType) {}
