package com.basarsy.skyline.reservation.service;

import java.util.Optional;
import java.util.Set;

/**
 * Seat numbering for a single-aisle, six-abreast cabin (rows 1..n, seats A-F), filled row by row up
 * to the aircraft's total capacity. Kept deliberately simple until aircraft types carry a real cabin
 * layout; the point is that assigned seats always exist on the aircraft and never collide.
 */
final class SeatMap {

    static final String SEAT_LETTERS = "ABCDEF";

    private SeatMap() {}

    /** First seat (row-major) not in {@code taken}, or empty when the cabin is full. */
    static Optional<String> firstFree(int totalSeats, Set<String> taken) {
        for (int index = 0; index < totalSeats; index++) {
            String seat = seatAt(index);
            if (!taken.contains(seat)) {
                return Optional.of(seat);
            }
        }
        return Optional.empty();
    }

    static String seatAt(int index) {
        int row = index / SEAT_LETTERS.length() + 1;
        char letter = SEAT_LETTERS.charAt(index % SEAT_LETTERS.length());
        return row + String.valueOf(letter);
    }
}
