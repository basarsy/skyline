package com.basarsy.skyline.checkin.dto;

import java.time.LocalDateTime;

public record BoardingPassResponse(
    String pnr,
    String passengerName,
    String flightNumber,
    String origin,
    String destination,
    LocalDateTime departureTime,
    LocalDateTime boardingTime,
    String gate,
    String seatNumber,
    String barcodeData,
    LocalDateTime issuedAt
) {}
