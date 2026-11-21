package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.repository.BlockedSeatRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seats per cabin. The schedule service only counts seats per flight, so without this a Business ticket could be
 * sold on an all-Economy aircraft, or more Business tickets than Business seats. With a layout, each cabin can sell
 * at most its seats (minus blocked ones). Concurrent bookings of one cabin are serialized with a transaction-scoped
 * advisory lock, so two last-seat bookings cannot both pass the count.
 *
 * <p>Aircraft types without a layout keep the old behaviour: any cabin, limited only by the flight's seat count.
 */
@Component
@RequiredArgsConstructor
public class CabinInventory {

    private final SeatLayoutProvider layouts;
    private final ReservationRepository reservationRepository;
    private final BlockedSeatRepository blockedSeatRepository;
    private final JdbcTemplate jdbc;

    /** Checks that {@code count} more passengers fit in the cabin, holding the cabin's lock until commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(FlightResponse flight, CabinClass cabin, int count) {
        Optional<SeatLayout> layout = layouts.forFlight(flight);
        if (layout.isEmpty()) {
            return;
        }
        Set<String> blocked = blockedSeatRepository.findSeatNumbers(flight.getId());
        long seats = layout.get().seats().stream()
                .filter(seat -> seat.cabin() == cabin)
                .map(Seat::code)
                .filter(code -> !blocked.contains(code))
                .count();
        if (layout.get().seats().stream().noneMatch(seat -> seat.cabin() == cabin)) {
            throw new SkylineException("This aircraft has no " + label(cabin) + " cabin",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, "cabin:" + flight.getId() + ":" + cabin);
        long sold = reservationRepository.countActiveInCabin(flight.getId(), cabin);
        if (sold + count > seats) {
            throw new SkylineException(label(cabin) + " is sold out on this flight", HttpStatus.CONFLICT);
        }
    }

    private static String label(CabinClass cabin) {
        String name = cabin.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
