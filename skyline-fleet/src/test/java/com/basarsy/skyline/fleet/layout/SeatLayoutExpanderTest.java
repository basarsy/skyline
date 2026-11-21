package com.basarsy.skyline.fleet.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.layout.CabinLayout.BodyType;
import com.basarsy.skyline.fleet.layout.CabinLayout.Facility;
import com.basarsy.skyline.fleet.layout.CabinLayout.FacilitySide;
import com.basarsy.skyline.fleet.layout.CabinLayout.FacilityType;
import com.basarsy.skyline.fleet.layout.CabinLayout.Section;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.Legroom;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.Seat;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SeatCategory;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SeatFeature;
import com.basarsy.skyline.fleet.layout.SeatLayoutResponse.SeatPosition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SeatLayoutExpanderTest {

    private static final UUID ID = UUID.randomUUID();

    private static SeatLayoutResponse expand(int totalSeats, CabinLayout layout) {
        return SeatLayoutExpander.expand(ID, "Test", "T1", totalSeats, layout);
    }

    private static CabinLayout layout(List<Section> sections, List<Integer> exits, List<Facility> facilities,
            List<String> omit) {
        return new CabinLayout(BodyType.NARROW, sections, List.of(), exits, null, facilities, omit, List.of());
    }

    private static Map<String, Seat> byCode(SeatLayoutResponse response) {
        return response.seats().stream().collect(Collectors.toMap(Seat::code, Function.identity()));
    }

    @Test
    void positionsFollowTheAisles() {
        // 3-3
        assertThat(List.of(0, 1, 2).stream().map(s -> SeatLayoutExpander.position(2, 0, 3, s)))
                .containsExactly(SeatPosition.WINDOW, SeatPosition.MIDDLE, SeatPosition.AISLE);
        // 2-4-2 middle block: aisle, middle, middle, aisle
        assertThat(List.of(0, 1, 2, 3).stream().map(s -> SeatLayoutExpander.position(3, 1, 4, s)))
                .containsExactly(SeatPosition.AISLE, SeatPosition.MIDDLE, SeatPosition.MIDDLE, SeatPosition.AISLE);
        // 1-2-1: the lone outer seats are windows
        assertThat(SeatLayoutExpander.position(3, 0, 1, 0)).isEqualTo(SeatPosition.WINDOW);
        assertThat(SeatLayoutExpander.position(3, 2, 1, 0)).isEqualTo(SeatPosition.WINDOW);
    }

    @Test
    void derivesFlagsLegroomAndCategory() {
        var response = expand(24, layout(
                List.of(new Section(CabinClass.ECONOMY, 1, 4, "ABC|DEF", 2, List.of())),
                List.of(3),
                List.of(new Facility(FacilityType.LAVATORY, 4, FacilitySide.LEFT)),
                List.of()));
        Map<String, Seat> seats = byCode(response);

        Seat bulkhead = seats.get("1A");
        assertThat(bulkhead.features()).contains(SeatFeature.BULKHEAD);
        assertThat(bulkhead.legroom()).isEqualTo(Legroom.EXTRA);
        assertThat(bulkhead.category()).isEqualTo(SeatCategory.EXTRA_LEGROOM);

        assertThat(seats.get("2B").category()).isEqualTo(SeatCategory.FRONT);
        assertThat(seats.get("2B").features()).contains(SeatFeature.LIMITED_RECLINE); // in front of the exit row

        assertThat(seats.get("3C").category()).isEqualTo(SeatCategory.EXIT_ROW);
        assertThat(seats.get("3C").features()).contains(SeatFeature.EXIT_ROW);

        assertThat(seats.get("4A").category()).isEqualTo(SeatCategory.PREFERRED);
        assertThat(seats.get("4B").category()).isEqualTo(SeatCategory.STANDARD);
        assertThat(seats.get("4B").features()).contains(SeatFeature.NEAR_LAVATORY, SeatFeature.LIMITED_RECLINE);
    }

    @Test
    void skipRowsAndOmittedSeatsDoNotExist() {
        var response = expand(5, new CabinLayout(BodyType.NARROW,
                List.of(new Section(CabinClass.ECONOMY, 12, 14, "AB|C", 0, List.of())),
                List.of(13), List.of(), null, List.of(), List.of("12A"), List.of()));

        assertThat(response.seats()).extracting(Seat::code).containsExactly("12B", "12C", "14A", "14B", "14C");
    }

    @Test
    void seatCountMustMatchTheAircraftType() {
        var layout = layout(List.of(new Section(CabinClass.ECONOMY, 1, 2, "ABC|DEF", 0, List.of())), List.of(),
                List.of(), List.of());

        assertThatThrownBy(() -> expand(13, layout))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("has 12 seats but the aircraft type has 13")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsOverlappingSectionsUnknownRowsAndDuplicateLetters() {
        Section first = new Section(CabinClass.BUSINESS, 1, 3, "AC|DF", 0, List.of());
        assertThatThrownBy(() -> expand(0, layout(
                List.of(first, new Section(CabinClass.ECONOMY, 3, 5, "ABC|DEF", 0, List.of())),
                List.of(), List.of(), List.of())))
                .hasMessageContaining("without overlapping");
        assertThatThrownBy(() -> expand(12, layout(List.of(first), List.of(9), List.of(), List.of())))
                .hasMessageContaining("exitRows: row 9");
        assertThatThrownBy(() -> expand(12, layout(List.of(new Section(CabinClass.ECONOMY, 1, 3, "AB|BC", 0, List.of())),
                List.of(), List.of(), List.of())))
                .hasMessageContaining("appears twice");
        assertThatThrownBy(() -> expand(12, layout(List.of(first), List.of(), List.of(), List.of("7A"))))
                .hasMessageContaining("omitSeats: 7A");
    }

    // --- The seeded layouts (V4 migration), checked without a database ---

    @Test
    void everySeededLayoutExpandsToExactlyItsTotalSeats() throws IOException {
        Map<String, CabinLayout> seeds = seededLayouts();
        assertThat(seeds).hasSize(4);

        assertThat(expand(180, seeds.get("201")).seats()).hasSize(180);
        assertThat(expand(189, seeds.get("202")).seats()).hasSize(189);
        assertThat(expand(296, seeds.get("203")).seats()).hasSize(296);
        assertThat(expand(190, seeds.get("204")).seats()).hasSize(190);
    }

    @Test
    void theSeededTypesAreClearlyDifferent() throws IOException {
        Map<String, CabinLayout> seeds = seededLayouts();

        var a320 = expand(180, seeds.get("201"));
        assertThat(a320.body()).isEqualTo(BodyType.NARROW);
        assertThat(a320.rows()).extracting(SeatLayoutResponse.RowView::number).doesNotContain(13);
        assertThat(byCode(a320).get("11A").category()).isEqualTo(SeatCategory.EXIT_ROW);

        var b787 = expand(296, seeds.get("203"));
        assertThat(b787.body()).isEqualTo(BodyType.WIDE);
        assertThat(b787.sections()).extracting(SeatLayoutResponse.SectionView::cabin)
                .containsExactly(CabinClass.BUSINESS, CabinClass.ECONOMY);
        assertThat(b787.seats()).filteredOn(s -> s.cabin() == CabinClass.BUSINESS).hasSize(30);
        assertThat(byCode(b787).get("20E").position()).isEqualTo(SeatPosition.MIDDLE);
        assertThat(byCode(b787).get("20F").position()).isEqualTo(SeatPosition.AISLE);

        var a321 = expand(190, seeds.get("204"));
        assertThat(a321.seats()).filteredOn(s -> s.cabin() == CabinClass.BUSINESS).hasSize(16);
        assertThat(byCode(a321).get("5A").features()).contains(SeatFeature.BULKHEAD, SeatFeature.NEAR_LAVATORY);
    }

    /** The layout JSON of each UPDATE in the seed migration, keyed by the last three digits of the type id. */
    static Map<String, CabinLayout> seededLayouts() throws IOException {
        String sql;
        try (InputStream in = SeatLayoutExpanderTest.class.getResourceAsStream("/db/migration/fleet/V4__cabin_layouts.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("SET cabin_layout = '(\\{.*?})'::jsonb WHERE id = '[0-9-]+(\\d{3})'",
                Pattern.DOTALL).matcher(sql);
        ObjectMapper json = new ObjectMapper();
        Map<String, CabinLayout> layouts = new LinkedHashMap<>();
        while (m.find()) {
            layouts.put(m.group(2), json.readValue(m.group(1), CabinLayout.class));
        }
        return layouts;
    }
}
