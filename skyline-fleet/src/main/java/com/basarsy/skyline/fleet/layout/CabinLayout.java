package com.basarsy.skyline.fleet.layout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * The cabin layout of an aircraft type, as stored (JSONB on {@code aircraft_types.cabin_layout}) and as an admin
 * edits it. It is compact on purpose: sections give row ranges and seat letters grouped by aisle, and every seat's
 * position, legroom, flags and price category are derived from it by {@link SeatLayoutExpander}, so the seat map,
 * pricing and seat assignment can never disagree about what a seat is.
 *
 * @param body         narrow-body (one aisle) or wide-body (two aisles); drives the fuselage drawing
 * @param sections     cabins front to back, non-overlapping row ranges
 * @param skipRows     row numbers that don't exist (e.g. 13)
 * @param exitRows     rows next to an emergency exit
 * @param wing         rows over the wing, for the drawing and the "over wing" hint
 * @param facilities   lavatories, galleys and closets, placed between rows
 * @param omitSeats    seats that a section's letters would create but the aircraft doesn't have, e.g. "1A"
 * @param accessibleSeats seats with movable armrests for passengers with reduced mobility
 */
public record CabinLayout(
        @NotNull BodyType body,
        @NotEmpty @Valid List<Section> sections,
        List<Integer> skipRows,
        List<Integer> exitRows,
        @Valid WingSpan wing,
        @Valid List<Facility> facilities,
        List<String> omitSeats,
        List<String> accessibleSeats) {

    public enum BodyType { NARROW, WIDE }

    public enum FacilityType { LAVATORY, GALLEY, CLOSET }

    /** Where a facility sits across the cabin. FULL spans the width (e.g. a rear galley). */
    public enum FacilitySide { LEFT, CENTER, RIGHT, FULL }

    /**
     * @param columns          seat letters grouped by aisle, e.g. {@code ABC|DEF} (3-3) or {@code ABC|DEFG|HJK}
     * @param frontRows        how many of the section's first rows are priced as "front of cabin"
     * @param extraLegroomRows rows with extra pitch beyond exits and bulkheads
     */
    public record Section(
            @NotNull CabinClass cabin,
            @Min(1) @Max(99) int fromRow,
            @Min(1) @Max(99) int toRow,
            @NotNull @Pattern(regexp = "[A-Z]{1,4}(\\|[A-Z]{1,4}){0,3}",
                    message = "columns must be 1-4 groups of seat letters separated by '|', e.g. ABC|DEF")
            String columns,
            @Min(0) int frontRows,
            List<Integer> extraLegroomRows) {}

    public record WingSpan(@Min(1) int fromRow, @Min(1) int toRow) {}

    /** @param afterRow the row the facility sits behind; 0 means in front of the first row */
    public record Facility(@NotNull FacilityType type, @Min(0) int afterRow, @NotNull FacilitySide side) {}

    public List<Integer> skipRows() { return skipRows == null ? List.of() : skipRows; }

    public List<Integer> exitRows() { return exitRows == null ? List.of() : exitRows; }

    public List<Facility> facilities() { return facilities == null ? List.of() : facilities; }

    public List<String> omitSeats() { return omitSeats == null ? List.of() : omitSeats; }

    public List<String> accessibleSeats() { return accessibleSeats == null ? List.of() : accessibleSeats; }
}
