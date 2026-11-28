package com.basarsy.skyline.route.dto;

import com.basarsy.skyline.route.validation.IanaTimeZone;
import jakarta.validation.constraints.NotBlank;

/** Corrects an airport's time zone (for one stored before zones were validated). */
public record AirportTimezoneRequest(@NotBlank @IanaTimeZone String timezone) {}
