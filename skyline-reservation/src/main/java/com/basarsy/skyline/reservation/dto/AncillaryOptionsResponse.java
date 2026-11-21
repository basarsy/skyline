package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.RouteScope;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import java.util.List;
import java.util.UUID;

/**
 * The add-ons one passenger can buy for one flight, and what the fare already includes.
 *
 * @param included the fare's allowance, e.g. "Cabin bag x1", so the page can say what's already covered
 */
public record AncillaryOptionsResponse(
        UUID reservationId,
        UUID flightId,
        RouteScope routeScope,
        SalesChannel channel,
        String currency,
        List<Included> included,
        List<AncillaryOffer> offers) {

    public record Included(String code, String name, int quantity) {}
}
