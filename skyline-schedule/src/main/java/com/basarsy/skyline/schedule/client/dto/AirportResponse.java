package com.basarsy.skyline.schedule.client.dto;

import java.util.UUID;

public record AirportResponse(
        UUID id,
        String iataCode,
        String icaoCode,
        String name,
        String city,
        String country,
        String timezone) {
}
