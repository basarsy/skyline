package com.basarsy.skyline.crew.client.dto;

import java.util.UUID;

public record AirportSummary(UUID id, String iataCode, String name, String city, String country) {
}
