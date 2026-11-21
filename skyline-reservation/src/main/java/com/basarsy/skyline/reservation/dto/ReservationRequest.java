package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** @param fareType optional; STANDARD when omitted */
public record ReservationRequest(
        @NotNull(message = "Flight ID is required")
        UUID flightId,

        @NotNull(message = "Passenger details are required")
        @Valid
        PassengerRequest passenger,

        @NotNull(message = "Cabin class is required")
        CabinClass cabinClass,

        FareType fareType
) {
    public ReservationRequest(UUID flightId, PassengerRequest passenger, CabinClass cabinClass) {
        this(flightId, passenger, cabinClass, null);
    }
}
