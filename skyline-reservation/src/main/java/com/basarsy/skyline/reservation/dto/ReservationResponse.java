package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    private UUID id;
    private String pnr;
    private PassengerResponse passenger;
    private FlightResponse flight;
    private ReservationStatus status;
    private String seatNumber;
    private CabinClass cabinClass;
    private BigDecimal totalPrice;
    private LocalDateTime bookedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
