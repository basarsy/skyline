package com.basarsy.skyline.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.reservation.SeatTestFixtures;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.entity.CabinClass;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Check-in seat assignment on the fixture aircraft (Business row 1, Economy rows 2-5, exit row 4, bulkhead 2). */
class SeatAssignerTest {

    private final SeatLayout layout = SeatTestFixtures.layout();
    private final Predicate<Seat> economy = seat -> seat.cabin() == CabinClass.ECONOMY;
    /** As seeded: ordinary seats only, never extra legroom or exit rows. */
    private final Predicate<Seat> standardPriced = seat ->
            seat.category() == SeatCategory.STANDARD || seat.category() == SeatCategory.PREFERRED;

    @Test
    void onlyHandsOutStandardPricedSeatsInTheOwnCabin() {
        Set<String> picked = IntStream.range(0, 200)
                .mapToObj(i -> SeatAssigner.pick(layout, economy, standardPriced, Set.of(), List.of(),
                        new SplittableRandom(i)).orElseThrow())
                .collect(Collectors.toSet());

        // Rows 3 and 5 only: row 1 is Business, row 2 the paid bulkhead, row 4 the paid exit row.
        assertThat(picked).allMatch(code -> code.startsWith("3") || code.startsWith("5"));
        assertThat(picked).hasSizeGreaterThan(6); // random, not always the same seat
    }

    @Test
    void neverPicksATakenBlockedOrHeldSeat() {
        Set<String> unavailable = new HashSet<>(Set.of("3A", "3B", "3C", "3D", "3E", "3F", "5A", "5B", "5C", "5D", "5E"));

        assertThat(SeatAssigner.pick(layout, economy, standardPriced, unavailable, List.of(), new SplittableRandom(1)))
                .contains("5F");
    }

    @Test
    void fallsBackToPaidSeatsInTheCabinOnlyWhenNoStandardSeatIsLeft() {
        Set<String> unavailable = new HashSet<>();
        for (String letter : List.of("A", "B", "C", "D", "E", "F")) {
            unavailable.add("3" + letter);
            unavailable.add("5" + letter);
        }

        String seat = SeatAssigner.pick(layout, economy, standardPriced, unavailable, List.of(), new SplittableRandom(1))
                .orElseThrow();
        assertThat(seat).matches("[24][A-F]");
    }

    @Test
    void aFullCabinHasNoSeatEvenIfAnotherCabinHas() {
        Set<String> allEconomy = layout.seats().stream().filter(economy).map(Seat::code).collect(Collectors.toSet());

        assertThat(SeatAssigner.pick(layout, economy, standardPriced, allEconomy, List.of(), new SplittableRandom(1)))
                .isEmpty();
    }

    @Test
    void sitsPassengersOfTheSameBookingTogether() {
        // A companion in 3A: the seat right next to them (3B) beats everything else, every time.
        for (int i = 0; i < 20; i++) {
            assertThat(SeatAssigner.pick(layout, economy, standardPriced, Set.of("3A"), List.of("3A"),
                    new SplittableRandom(i))).contains("3B");
        }
        // 3A and 3B taken by the family: 3C next, then across the aisle (3D) before the row behind.
        assertThat(SeatAssigner.pick(layout, economy, standardPriced, Set.of("3A", "3B"), List.of("3A", "3B"),
                new SplittableRandom(3))).contains("3C");
        assertThat(SeatAssigner.pick(layout, economy, standardPriced, Set.of("3A", "3B", "3C"),
                List.of("3A", "3B", "3C"), new SplittableRandom(3))).contains("3D");
    }

    @Test
    void columnsCountTheAisleAsAPosition() {
        var columns = SeatAssigner.columnIndexes(layout);

        assertThat(columns.get("3C")).isEqualTo(2);
        assertThat(columns.get("3D")).isEqualTo(4);
        assertThat(columns.get("1C")).isEqualTo(1);
    }
}
