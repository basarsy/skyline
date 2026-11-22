package com.basarsy.skyline.reservation.client.dto;

import com.basarsy.skyline.reservation.client.dto.RouteResponse;
import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
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
public class FlightResponse {
    private UUID id;
    private String flightNumber;
    private RouteResponse route;
    private AircraftResponse aircraft;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    /** Expected departure while delayed, else null; {@code departureTime} stays the scheduled time. */
    private LocalDateTime estimatedDepartureTime;
    private LocalDateTime estimatedArrivalTime;
    /** Departure gate, null until ops assign one. */
    private String gate;
    private FlightStatus status;
    private int availableSeats;
    private BigDecimal basePrice;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
