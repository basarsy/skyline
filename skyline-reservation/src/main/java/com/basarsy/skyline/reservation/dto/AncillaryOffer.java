package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.AncillaryType;
import java.math.BigDecimal;

/**
 * One catalog product as this passenger on this flight can buy it now.
 *
 * @param includedQuantity  how many the fare already includes (shown so nobody pays twice)
 * @param purchasedQuantity how many are already bought
 * @param maxAdditional     how many more can be bought right now (0 when not available)
 * @param price             the unit price on this channel; {@code onlinePrice}/{@code airportPrice} for comparison
 * @param reason            why it can't be bought, when {@code available} is false
 */
public record AncillaryOffer(
        String code,
        AncillaryType type,
        String name,
        String description,
        Integer weightKg,
        int includedQuantity,
        int purchasedQuantity,
        int maxAdditional,
        BigDecimal price,
        BigDecimal onlinePrice,
        BigDecimal airportPrice,
        boolean available,
        String reason) {}
