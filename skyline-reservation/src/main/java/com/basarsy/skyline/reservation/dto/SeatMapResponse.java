package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.client.dto.SeatLayout.Facility;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Legroom;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Row;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatPosition;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Section;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.WingSpan;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the seat map draws, for one passenger: the aircraft's layout, each seat's live status, and the
 * passenger's price for every seat in their cabin. Seats in other cabins come back with no price and are not
 * selectable.
 *
 * @param cabin            the cabin the prices are for
 * @param surchargePercent the nearly-full surcharge included in paid seat prices (0, 15 or 30)
 * @param holdSeconds      how long a seat stays held once chosen
 * @param legend           one entry per category sold in the passenger's cabin, cheapest first
 */
public record SeatMapResponse(
        UUID flightId,
        String flightNumber,
        UUID aircraftTypeId,
        String aircraftName,
        String body,
        WingSpan wing,
        List<Section> sections,
        List<Row> rows,
        List<Facility> facilities,
        CabinClass cabin,
        FareType fareType,
        String currency,
        int surchargePercent,
        long holdSeconds,
        List<PriceBand> legend,
        List<SeatView> seats) {

    public enum SeatStatus { AVAILABLE, HELD, TAKEN, BLOCKED }

    /**
     * @param price      the passenger's price, null outside their cabin
     * @param selectable whether this passenger can choose it now
     * @param mine       held or booked by this passenger (the given reservation or passenger slot)
     */
    public record SeatView(
            String code,
            int row,
            String letter,
            CabinClass cabin,
            SeatPosition position,
            Legroom legroom,
            Set<String> features,
            SeatCategory category,
            SeatStatus status,
            BigDecimal price,
            boolean selectable,
            boolean mine) {}

    public record PriceBand(SeatCategory category, BigDecimal price, int available) {}
}
