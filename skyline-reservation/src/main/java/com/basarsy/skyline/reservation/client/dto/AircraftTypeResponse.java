package com.basarsy.skyline.reservation.client.dto;

import java.util.UUID;

public record AircraftTypeResponse(
        UUID id, String manufacturer, String model, int totalSeats, String cabinConfig) {}
