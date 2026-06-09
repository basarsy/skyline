package com.basarsy.skyline.schedule.client.dto;

import com.basarsy.skyline.schedule.client.dto.AircraftStatus;
import java.util.UUID;

public record AircraftResponse(
        UUID id,
        String tailNumber,
        AircraftTypeSummary aircraftType,
        AircraftStatus status,
        int manufacturedYear) {}
