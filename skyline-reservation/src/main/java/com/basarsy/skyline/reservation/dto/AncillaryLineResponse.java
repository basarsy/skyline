package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.ReservationAncillary;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One line item of a reservation: a seat, a bag, ... as it was bought. */
public record AncillaryLineResponse(
        UUID id,
        AncillaryType type,
        String productCode,
        String description,
        String seatNumber,
        int quantity,
        BigDecimal price,
        SalesChannel channel,
        ReservationAncillary.Status status,
        boolean refundable,
        LocalDateTime createdAt) {

    public static AncillaryLineResponse of(ReservationAncillary line) {
        return new AncillaryLineResponse(line.getId(), line.getType(), line.getProductCode(), line.getDescription(),
                line.getSeatNumber(), line.getQuantity(), line.getPrice(), line.getChannel(), line.getStatus(),
                line.isRefundable(), line.getCreatedAt());
    }
}
