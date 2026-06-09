package com.basarsy.skyline.checkin.client.dto;

import com.basarsy.skyline.checkin.client.dto.AircraftStatus;
import java.util.UUID;

public record AircraftResponse(
        UUID id,
        String tailNumber,
        AircraftTypeSummary aircraftType,
        AircraftStatus status,
        int manufacturedYear) {}
