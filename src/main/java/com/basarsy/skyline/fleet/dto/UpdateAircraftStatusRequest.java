package com.basarsy.skyline.fleet.dto;

import com.basarsy.skyline.fleet.entity.AircraftStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAircraftStatusRequest(@NotNull AircraftStatus status) {}
