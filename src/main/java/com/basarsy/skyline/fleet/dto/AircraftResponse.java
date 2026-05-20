package com.basarsy.skyline.fleet.dto;

import com.basarsy.skyline.fleet.entity.AircraftStatus;
import java.util.UUID;

public record AircraftResponse(
        UUID id,
        String tailNumber,
        AircraftTypeSummary aircraftType,
        AircraftStatus status,
        int manufacturedYear) {}
