package com.basarsy.skyline.schedule.client.dto;

import java.util.UUID;

public record AircraftTypeSummary(UUID id, String manufacturer, String model, int totalSeats) {}
