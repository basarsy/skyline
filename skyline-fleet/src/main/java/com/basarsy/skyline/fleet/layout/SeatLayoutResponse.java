package com.basarsy.skyline.fleet.layout;

import com.basarsy.skyline.fleet.layout.CabinLayout.BodyType;
import com.basarsy.skyline.fleet.layout.CabinLayout.Facility;
import com.basarsy.skyline.fleet.layout.CabinLayout.WingSpan;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A layout expanded into every seat: what the seat map draws and what pricing and seat assignment read.
 *
 * @param sections cabins front to back, with their seat letters grouped by aisle
 * @param rows     every existing row, front to back
 * @param seats    every seat, front to back and left to right; exactly {@code totalSeats} of them
 */
public record SeatLayoutResponse(
        UUID aircraftTypeId,
        String manufacturer,
        String model,
        int totalSeats,
        BodyType body,
        WingSpan wing,
        List<SectionView> sections,
        List<RowView> rows,
        List<Facility> facilities,
        List<Seat> seats) {

    public record SectionView(CabinClass cabin, int fromRow, int toRow, List<List<String>> columnGroups) {}

    public record RowView(int number, CabinClass cabin, boolean exit, boolean overWing) {}

    public record Seat(
            String code,
            int row,
            String letter,
            CabinClass cabin,
            SeatPosition position,
            Legroom legroom,
            Set<SeatFeature> features,
            SeatCategory category) {}

    public enum SeatPosition { WINDOW, MIDDLE, AISLE }

    public enum Legroom { STANDARD, EXTRA }

    public enum SeatFeature { EXIT_ROW, BULKHEAD, NEAR_LAVATORY, NEAR_GALLEY, LIMITED_RECLINE, ACCESSIBLE, OVER_WING }

    /**
     * The price category, most specific first: an exit-row seat is EXIT_ROW even though it also has extra legroom.
     * PREFERRED is a window or aisle seat in an ordinary row; STANDARD is everything else.
     */
    public enum SeatCategory { EXIT_ROW, EXTRA_LEGROOM, FRONT, PREFERRED, STANDARD }
}
