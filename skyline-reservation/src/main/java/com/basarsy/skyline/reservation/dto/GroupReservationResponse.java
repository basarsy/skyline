package com.basarsy.skyline.reservation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @param reservations leg by leg, passenger by passenger; @param totalPrice everything, fares and extras */
public record GroupReservationResponse(
        UUID bookingGroupId, List<ReservationResponse> reservations, BigDecimal totalPrice, String currency) {}
