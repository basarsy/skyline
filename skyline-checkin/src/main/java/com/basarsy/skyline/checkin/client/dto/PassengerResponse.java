package com.basarsy.skyline.checkin.client.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PassengerResponse(
        UUID id,
        String firstName,
        String lastName,
        String passportNumber,
        LocalDate dateOfBirth,
        String nationality
) {}
