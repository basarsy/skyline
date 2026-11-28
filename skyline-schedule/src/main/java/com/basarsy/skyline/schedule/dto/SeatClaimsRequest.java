package com.basarsy.skyline.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The claims of one booking's passengers on one flight: held, or given back, together. */
public record SeatClaimsRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> claimIds) {}
