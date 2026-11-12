package com.basarsy.skyline.reservation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PassengerResponse(
        UUID id,
        /* Owner of the booking; lets other services (check-in) verify ownership. */
        UUID userId,
        String firstName,
        String lastName,
        String passportNumber,
        LocalDate dateOfBirth,
        String nationality
) {}
