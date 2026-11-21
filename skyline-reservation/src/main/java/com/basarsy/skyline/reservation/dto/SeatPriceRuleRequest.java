package com.basarsy.skyline.reservation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** @param price 0 = included in the fare; @param autoAssign whether check-in may give this category away free */
public record SeatPriceRuleRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull Boolean autoAssign) {}
