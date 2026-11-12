package com.basarsy.skyline.checkin.client.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PassengerResponse(
        UUID id,
        /* Owner of the booking, used to enforce that only they (or staff) can check in. */
        UUID userId,
        String firstName,
        String lastName,
        String passportNumber,
        LocalDate dateOfBirth,
        String nationality
) {}
