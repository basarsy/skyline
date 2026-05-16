package com.basarsy.skyline.route.dto;

import java.util.UUID;

public record AirportSummary(UUID id, String iataCode, String name, String city, String country) {
}
