package com.basarsy.skyline.crew.client.dto;

import com.basarsy.skyline.crew.client.dto.AircraftStatus;
import java.util.UUID;

public record AircraftResponse(
        UUID id,
        String tailNumber,
        AircraftTypeSummary aircraftType,
        AircraftStatus status,
        int manufacturedYear) {}
