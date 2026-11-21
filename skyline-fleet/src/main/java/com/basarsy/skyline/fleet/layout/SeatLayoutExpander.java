package com.basarsy.skyline.fleet.layout;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.layout.CabinLayout.Facility;
import com.basarsy.skyline.fleet.layout.CabinLayout.FacilityType;
import com.basarsy.skyline.fleet.layout.CabinLayout.Section;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.Legroom;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.RowView;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.Seat;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SeatCategory;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SeatFeature;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SeatPosition;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SectionView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Turns a compact {@link CabinLayout} into every seat with its position, legroom, flags and price category, and
 * rejects layouts that don't describe a real cabin. Pure and deterministic: the same layout always yields the same
 * seats, which is what lets seat holds, pricing and check-in assignment agree on seat codes.
 *
 * <p>Derived rules: a section's first row, and any row right behind a facility, is a bulkhead; exit rows and
 * bulkheads have extra legroom; a row in front of an exit row or a facility, and the last row, has limited
 * recline; a row next to a lavatory or galley is flagged so.
 */
public final class SeatLayoutExpander {

    private SeatLayoutExpander() {}

    public static SeatLayoutResponse expand(UUID typeId, String manufacturer, String model, int totalSeats,
            CabinLayout layout) {
        List<Section> sections = layout.sections();
        validateSections(sections);

        Set<Integer> skip = new HashSet<>(layout.skipRows());
        // Every existing row, front to back, with the section it belongs to.
        Map<Integer, Section> rowSection = new LinkedHashMap<>();
        for (Section section : sections) {
            int before = rowSection.size();
            for (int row = section.fromRow(); row <= section.toRow(); row++) {
                if (!skip.contains(row)) {
                    rowSection.put(row, section);
                }
            }
            if (rowSection.size() == before) {
                throw invalid("Section " + section.cabin() + " " + section.fromRow() + "-" + section.toRow()
                        + " has no rows left after skipRows");
            }
        }
        List<Integer> rows = List.copyOf(rowSection.keySet());
        Set<Integer> rowSet = rowSection.keySet();

        Set<Integer> exits = requireRows("exitRows", layout.exitRows(), rowSet);
        for (Section section : sections) {
            requireRows("extraLegroomRows", nullToEmpty(section.extraLegroomRows()), rowSet);
        }
        if (layout.wing() != null && (layout.wing().fromRow() > layout.wing().toRow())) {
            throw invalid("wing.fromRow must not be after wing.toRow");
        }
        for (Facility facility : layout.facilities()) {
            if (facility.afterRow() != 0 && !rowSet.contains(facility.afterRow())) {
                throw invalid("Facility afterRow " + facility.afterRow() + " is not a row of this aircraft (0 = front)");
            }
        }

        Set<String> omit = new HashSet<>(layout.omitSeats());
        Set<String> accessible = new HashSet<>(layout.accessibleSeats());
        List<Seat> seats = new ArrayList<>();
        List<RowView> rowViews = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int row = rows.get(i);
            int previous = i == 0 ? 0 : rows.get(i - 1);
            Integer next = i + 1 < rows.size() ? rows.get(i + 1) : null;
            Section section = rowSection.get(row);
            int indexInSection = (int) rows.stream().filter(r -> rowSection.get(r) == section && r < row).count();

            boolean exit = exits.contains(row);
            boolean overWing = layout.wing() != null && row >= layout.wing().fromRow() && row <= layout.wing().toRow();
            boolean facilityAhead = layout.facilities().stream().anyMatch(f -> f.afterRow() == previous);
            boolean facilityBehind = layout.facilities().stream().anyMatch(f -> f.afterRow() == row);
            boolean bulkhead = indexInSection == 0 || facilityAhead;
            boolean limitedRecline = next == null || exits.contains(next) || facilityBehind;
            boolean extraLegroom = exit || bulkhead || nullToEmpty(section.extraLegroomRows()).contains(row);
            boolean front = indexInSection < section.frontRows();

            EnumSet<SeatFeature> rowFeatures = EnumSet.noneOf(SeatFeature.class);
            if (exit) rowFeatures.add(SeatFeature.EXIT_ROW);
            if (bulkhead) rowFeatures.add(SeatFeature.BULKHEAD);
            if (limitedRecline) rowFeatures.add(SeatFeature.LIMITED_RECLINE);
            if (overWing) rowFeatures.add(SeatFeature.OVER_WING);
            if (adjacentFacility(layout, FacilityType.LAVATORY, previous, row)) rowFeatures.add(SeatFeature.NEAR_LAVATORY);
            if (adjacentFacility(layout, FacilityType.GALLEY, previous, row)) rowFeatures.add(SeatFeature.NEAR_GALLEY);

            rowViews.add(new RowView(row, section.cabin(), exit, overWing));

            List<List<String>> groups = groups(section.columns());
            for (int g = 0; g < groups.size(); g++) {
                List<String> group = groups.get(g);
                for (int s = 0; s < group.size(); s++) {
                    String letter = group.get(s);
                    String code = row + letter;
                    if (omit.contains(code)) {
                        continue;
                    }
                    SeatPosition position = position(groups.size(), g, group.size(), s);
                    EnumSet<SeatFeature> features = EnumSet.copyOf(rowFeatures);
                    if (accessible.contains(code)) features.add(SeatFeature.ACCESSIBLE);
                    SeatCategory category = exit ? SeatCategory.EXIT_ROW
                            : extraLegroom ? SeatCategory.EXTRA_LEGROOM
                            : front ? SeatCategory.FRONT
                            : position != SeatPosition.MIDDLE ? SeatCategory.PREFERRED
                            : SeatCategory.STANDARD;
                    seats.add(new Seat(code, row, letter, section.cabin(), position,
                            extraLegroom ? Legroom.EXTRA : Legroom.STANDARD, features, category));
                }
            }
        }

        Set<String> codes = new HashSet<>();
        seats.forEach(seat -> codes.add(seat.code()));
        for (String code : omit) {
            if (!rowSectionHasSeat(rowSection, code)) {
                throw invalid("omitSeats: " + code + " is not a seat of this layout");
            }
        }
        for (String code : accessible) {
            if (!codes.contains(code)) {
                throw invalid("accessibleSeats: " + code + " is not a seat of this layout");
            }
        }
        if (seats.size() != totalSeats) {
            throw invalid("The layout has " + seats.size() + " seats but the aircraft type has " + totalSeats);
        }

        List<SectionView> sectionViews = sections.stream()
                .map(s -> new SectionView(s.cabin(), s.fromRow(), s.toRow(), groups(s.columns())))
                .toList();
        return new SeatLayoutResponse(typeId, manufacturer, model, totalSeats, layout.body(), layout.wing(),
                sectionViews, rowViews, layout.facilities(), seats);
    }

    private static void validateSections(List<Section> sections) {
        int lastRow = 0;
        for (Section section : sections) {
            if (section.fromRow() > section.toRow()) {
                throw invalid("Section " + section.cabin() + ": fromRow must not be after toRow");
            }
            if (section.fromRow() <= lastRow) {
                throw invalid("Sections must go front to back without overlapping rows");
            }
            lastRow = section.toRow();
            String letters = section.columns().replace("|", "");
            if (letters.chars().distinct().count() != letters.length()) {
                throw invalid("Section " + section.cabin() + ": a seat letter appears twice in " + section.columns());
            }
        }
    }

    /**
     * Window seats are the outer ends of the outer groups; a seat beside an aisle is an aisle seat; the rest are
     * middle seats. A lone seat in an outer group (1-2-1 business) counts as a window seat.
     */
    static SeatPosition position(int groupCount, int groupIndex, int groupSize, int seatIndex) {
        boolean firstGroup = groupIndex == 0;
        boolean lastGroup = groupIndex == groupCount - 1;
        if ((firstGroup && seatIndex == 0) || (lastGroup && seatIndex == groupSize - 1)) {
            return SeatPosition.WINDOW;
        }
        boolean aisleOnLeft = !firstGroup && seatIndex == 0;
        boolean aisleOnRight = !lastGroup && seatIndex == groupSize - 1;
        return aisleOnLeft || aisleOnRight ? SeatPosition.AISLE : SeatPosition.MIDDLE;
    }

    private static boolean adjacentFacility(CabinLayout layout, FacilityType type, int previousRow, int row) {
        return layout.facilities().stream()
                .anyMatch(f -> f.type() == type && (f.afterRow() == previousRow || f.afterRow() == row));
    }

    static List<List<String>> groups(String columns) {
        return Arrays.stream(columns.split("\\|"))
                .map(group -> group.chars().mapToObj(c -> String.valueOf((char) c)).toList())
                .toList();
    }

    private static boolean rowSectionHasSeat(Map<Integer, Section> rowSection, String code) {
        if (!code.matches("\\d{1,2}[A-Z]")) {
            return false;
        }
        int row = Integer.parseInt(code.substring(0, code.length() - 1));
        Section section = rowSection.get(row);
        return section != null && section.columns().indexOf(code.charAt(code.length() - 1)) >= 0;
    }

    private static Set<Integer> requireRows(String field, List<Integer> values, Set<Integer> rows) {
        for (Integer value : values) {
            if (!rows.contains(value)) {
                throw invalid(field + ": row " + value + " is not a row of this aircraft");
            }
        }
        return new HashSet<>(values);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static SkylineException invalid(String message) {
        return new SkylineException("Invalid cabin layout: " + message, HttpStatus.BAD_REQUEST);
    }
}
