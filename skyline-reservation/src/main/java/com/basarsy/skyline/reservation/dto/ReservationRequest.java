package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReservationRequest(
        @NotNull(message = "Flight ID is required")
        UUID flightId,
        
        @NotNull(message = "Passenger details are required")
        @Valid
        PassengerRequest passenger,
        
        @NotNull(message = "Cabin class is required")
        CabinClass cabinClass
) {}
