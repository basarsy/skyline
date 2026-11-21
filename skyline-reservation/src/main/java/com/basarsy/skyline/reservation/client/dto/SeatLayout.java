package com.basarsy.skyline.reservation.client.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An aircraft type's seats, expanded by the fleet service ({@code GET /aircraft-types/{id}/seat-layout}). Fleet
 * owns what a seat is (position, legroom, flags, price category); this service adds availability and prices.
 */
public record SeatLayout(
        UUID aircraftTypeId,
        String manufacturer,
        String model,
        int totalSeats,
        String body,
        WingSpan wing,
        List<Section> sections,
        List<Row> rows,
        List<Facility> facilities,
        List<Seat> seats) {

    public record WingSpan(int fromRow, int toRow) {}

    public record Section(CabinClass cabin, int fromRow, int toRow, List<List<String>> columnGroups) {}

    public record Row(int number, CabinClass cabin, boolean exit, boolean overWing) {}

    public record Facility(String type, int afterRow, String side) {}

    public record Seat(
            String code,
            int row,
            String letter,
            CabinClass cabin,
            SeatPosition position,
            Legroom legroom,
            Set<String> features,
            SeatCategory category) {}

    public enum SeatPosition { WINDOW, MIDDLE, AISLE }

    public enum Legroom { STANDARD, EXTRA }

    public enum SeatCategory { EXIT_ROW, EXTRA_LEGROOM, FRONT, PREFERRED, STANDARD }
}
