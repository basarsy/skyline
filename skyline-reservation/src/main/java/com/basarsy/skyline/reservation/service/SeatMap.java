package com.basarsy.skyline.reservation.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/**
 * Seat numbering for a single-aisle, six-abreast cabin (rows 1..n, seats A-F) up to the aircraft's
 * total capacity. Kept deliberately simple until aircraft types carry a real cabin layout (there are no
 * cabin sections yet, so a seat is not tied to the booked cabin class); the point is that assigned seats
 * always exist on the aircraft and never collide.
 */
final class SeatMap {

    static final String SEAT_LETTERS = "ABCDEF";

    private SeatMap() {}

    /**
     * A random seat not in {@code taken}, or empty when the cabin is full. Random rather than first-free so
     * that concurrent check-ins spread over the cabin instead of all colliding on the same seat.
     */
    static Optional<String> randomFree(int totalSeats, Set<String> taken, RandomGenerator random) {
        List<String> free = IntStream.range(0, totalSeats).mapToObj(SeatMap::seatAt)
                .filter(seat -> !taken.contains(seat))
                .toList();
        return free.isEmpty() ? Optional.empty() : Optional.of(free.get(random.nextInt(free.size())));
    }

    static String seatAt(int index) {
        int row = index / SEAT_LETTERS.length() + 1;
        char letter = SEAT_LETTERS.charAt(index % SEAT_LETTERS.length());
        return row + String.valueOf(letter);
    }
}
