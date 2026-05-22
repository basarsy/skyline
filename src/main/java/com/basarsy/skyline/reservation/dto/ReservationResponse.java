package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.schedule.dto.FlightResponse;
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
