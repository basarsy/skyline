package com.basarsy.skyline.checkin.client.dto;

import com.basarsy.skyline.checkin.client.dto.CabinClass;
import com.basarsy.skyline.checkin.client.dto.ReservationStatus;
import com.basarsy.skyline.checkin.client.dto.FlightResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String pnr,
        PassengerResponse passenger,
        FlightResponse flight,
        ReservationStatus status,
        String seatNumber,
        CabinClass cabinClass,
        BigDecimal totalPrice,
        LocalDateTime bookedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
