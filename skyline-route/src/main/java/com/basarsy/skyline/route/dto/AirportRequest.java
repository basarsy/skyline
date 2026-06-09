package com.basarsy.skyline.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AirportRequest(
        @NotBlank @Size(min = 3, max = 3) String iataCode,
        @NotBlank @Size(min = 4, max = 4) String icaoCode,
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String country,
        @NotBlank String timezone) {
}
