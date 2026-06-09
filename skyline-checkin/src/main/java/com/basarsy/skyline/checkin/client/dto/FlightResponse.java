package com.basarsy.skyline.checkin.client.dto;

import com.basarsy.skyline.checkin.client.dto.RouteResponse;
import com.basarsy.skyline.checkin.client.dto.AircraftResponse;
import com.basarsy.skyline.checkin.client.dto.FlightStatus;
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
    private FlightStatus status;
    private int availableSeats;
    private BigDecimal basePrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
