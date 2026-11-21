package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.RouteScope;
import java.math.BigDecimal;
import java.util.List;

/** A catalog product with its prices and the fares that include it (the admin's view of the catalog). */
public record AncillaryProductResponse(
        String code,
        AncillaryType type,
        String name,
        String description,
        Integer weightKg,
        int maxQuantity,
        int cutoffHours,
        boolean alwaysRefundable,
        boolean active,
        List<Price> prices,
        List<Allowance> includedIn,
        String currency) {

    public record Price(RouteScope routeScope, BigDecimal onlinePrice, BigDecimal airportPrice) {}

    public record Allowance(CabinClass cabinClass, FareType fareType, int quantity) {}
}
