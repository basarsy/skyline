package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Picks a free seat at check-in for a passenger who did not choose one.
 *
 * <ol>
 *   <li>Only seats in the passenger's own cabin, not taken, blocked or held by someone else.
 *   <li>Only seats the price table lets check-in give away ({@code auto_assign}: ordinary seats, never the paid
 *       extra-legroom or exit rows). Only if none of those is left does it fall back to any free seat in the
 *       cabin, so a passenger is never refused check-in while seats remain.
 *   <li>When others from the same booking already have seats on this flight, the closest seat to them wins
 *       (same row next to them first). Otherwise, and among equally close seats, it is random, so concurrent
 *       check-ins spread out instead of colliding on one seat.
 * </ol>
 */
final class SeatAssigner {

    private SeatAssigner() {}

    static Optional<String> pick(SeatLayout layout, Predicate<Seat> inCabin, Predicate<Seat> autoAssignable,
            Set<String> unavailable, Collection<String> companions, RandomGenerator random) {
        List<Seat> free = layout.seats().stream()
                .filter(inCabin)
                .filter(seat -> !unavailable.contains(seat.code()))
                .toList();
        List<Seat> preferred = free.stream().filter(autoAssignable).toList();
        List<Seat> candidates = preferred.isEmpty() ? free : preferred;
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Integer> columns = columnIndexes(layout);
        Map<String, Seat> byCode = new HashMap<>();
        layout.seats().forEach(seat -> byCode.put(seat.code(), seat));
        List<Seat> companionSeats = companions.stream().map(byCode::get).filter(s -> s != null).toList();
        if (companionSeats.isEmpty()) {
            return Optional.of(candidates.get(random.nextInt(candidates.size())).code());
        }

        int best = candidates.stream().mapToInt(seat -> distance(seat, companionSeats, columns)).min().orElseThrow();
        List<Seat> nearest = candidates.stream()
                .filter(seat -> distance(seat, companionSeats, columns) == best)
                .toList();
        return Optional.of(nearest.get(random.nextInt(nearest.size())).code());
    }

    /**
     * Distance to the nearest companion: rows count more than seats across, and an aisle counts as a seat, so
     * "next to them" beats "across the aisle" beats "the row behind".
     */
    static int distance(Seat seat, List<Seat> companions, Map<String, Integer> columns) {
        int best = Integer.MAX_VALUE;
        for (Seat other : companions) {
            int across = Math.abs(columns.getOrDefault(seat.code(), 0) - columns.getOrDefault(other.code(), 0));
            int rows = Math.abs(seat.row() - other.row());
            best = Math.min(best, rows * 4 + across);
        }
        return best;
    }

    /** Each seat's position across the cabin, counting aisles as a position (A=0, B=1, C=2, aisle, D=4, ...). */
    static Map<String, Integer> columnIndexes(SeatLayout layout) {
        Map<String, Map<String, Integer>> byCabin = new HashMap<>();
        Map<Integer, SeatLayout.Section> sectionOfRow = new HashMap<>();
        for (SeatLayout.Section section : layout.sections()) {
            Map<String, Integer> letters = new HashMap<>();
            int index = 0;
            for (List<String> group : section.columnGroups()) {
                for (String letter : group) {
                    letters.put(letter, index++);
                }
                index++; // the aisle
            }
            byCabin.put(section.cabin() + ":" + section.fromRow(), letters);
            for (int row = section.fromRow(); row <= section.toRow(); row++) {
                sectionOfRow.put(row, section);
            }
        }
        Map<String, Integer> columns = new HashMap<>();
        for (Seat seat : layout.seats()) {
            SeatLayout.Section section = sectionOfRow.get(seat.row());
            if (section != null) {
                columns.put(seat.code(), byCabin.get(section.cabin() + ":" + section.fromRow()).getOrDefault(seat.letter(), 0));
            }
        }
        return columns;
    }
}
