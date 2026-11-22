package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param fareType     optional; STANDARD when omitted
 * @param contactEmail optional; the account's email when omitted
 */
public record ReservationRequest(
        @NotNull(message = "Flight ID is required")
        UUID flightId,

        @NotNull(message = "Passenger details are required")
        @Valid
        PassengerRequest passenger,

        @NotNull(message = "Cabin class is required")
        CabinClass cabinClass,

        FareType fareType,

        @jakarta.validation.constraints.Email(message = "Contact email must be a valid email address")
        @jakarta.validation.constraints.Size(max = 254)
        String contactEmail
) {
    public ReservationRequest(UUID flightId, PassengerRequest passenger, CabinClass cabinClass) {
        this(flightId, passenger, cabinClass, null, null);
    }

    public ReservationRequest(UUID flightId, PassengerRequest passenger, CabinClass cabinClass, FareType fareType) {
        this(flightId, passenger, cabinClass, fareType, null);
    }
}
