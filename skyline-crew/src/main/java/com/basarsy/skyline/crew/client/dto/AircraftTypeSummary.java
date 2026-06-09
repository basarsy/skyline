package com.basarsy.skyline.crew.client.dto;

import java.util.UUID;

public record AircraftTypeSummary(UUID id, String manufacturer, String model, int totalSeats) {}
