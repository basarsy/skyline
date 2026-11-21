package com.basarsy.skyline.reservation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** A product's price on one route type. The airport price may not be below the online price. */
public record AncillaryPriceRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal onlinePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal airportPrice) {}
