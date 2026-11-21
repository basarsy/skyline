package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A held seat and what it will cost; the hold lapses at {@code expiresAt} (UTC) unless the seat is confirmed. */
public record SeatHoldResponse(
        UUID flightId, String seatNumber, SeatCategory category, BigDecimal price, String currency,
        LocalDateTime expiresAt) {}
