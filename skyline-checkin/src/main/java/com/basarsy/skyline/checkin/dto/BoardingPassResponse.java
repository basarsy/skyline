package com.basarsy.skyline.checkin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardingPassResponse {
    private String pnr;
    private String passengerName;
    private String flightNumber;
    private String origin;
    private String destination;
    /** Scheduled departure. */
    private LocalDateTime departureTime;
    /** Expected departure while the flight is delayed, else null. */
    private LocalDateTime estimatedDepartureTime;
    private LocalDateTime boardingTime;
    private String gate;
    private String seatNumber;
    private String barcodeData;
    private LocalDateTime issuedAt;
}
