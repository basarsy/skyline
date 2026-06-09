package com.basarsy.skyline.schedule.client.dto;

import java.util.UUID;

public record AircraftTypeResponse(
        UUID id, String manufacturer, String model, int totalSeats, String cabinConfig) {}
