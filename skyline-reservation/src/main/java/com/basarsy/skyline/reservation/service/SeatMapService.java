package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.dto.SeatMapResponse;
import com.basarsy.skyline.reservation.dto.SeatMapResponse.PriceBand;
import com.basarsy.skyline.reservation.dto.SeatMapResponse.SeatStatus;
import com.basarsy.skyline.reservation.dto.SeatMapResponse.SeatView;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.repository.BlockedSeatRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import com.basarsy.skyline.reservation.service.SeatHoldStore.HoldOwner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a flight's seat map for one passenger. Availability is derived, not stored per flight: TAKEN from the
 * reservations' seat numbers, HELD from the Redis holds, BLOCKED from {@code blocked_seats}.
 */
@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final ScheduleClient scheduleClient;
    private final SeatLayoutProvider layouts;
    private final SeatPricing seatPricing;
    private final SeatHoldStore holds;
    private final ReservationRepository reservationRepository;
    private final BlockedSeatRepository blockedSeatRepository;
    private final PricingProperties pricing;

    /**
     * For an existing booking pass {@code reservationId} (cabin and fare come from it). Before booking, pass the
     * cabin and fare being bought and the passenger's position in the booking (0 for the first passenger).
     */
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(UUID flightId, UUID reservationId, CabinClass cabin, FareType fareType,
            int passengerIndex) {
        UUID userId = CallerAccess.currentUserId();
        CabinClass pricedCabin;
        FareType pricedFare;
        HoldOwner owner;
        String bookedSeat = null;
        if (reservationId != null) {
            Reservation reservation = ownedReservationOnFlight(reservationId, flightId);
            pricedCabin = reservation.getCabinClass();
            pricedFare = reservation.getFareType();
            owner = HoldOwner.forReservation(userId, reservationId);
            bookedSeat = reservation.getSeatNumber();
        } else {
            pricedCabin = cabin == null ? CabinClass.ECONOMY : cabin;
            pricedFare = fareType == null ? FareType.STANDARD : fareType;
            owner = HoldOwner.forPassenger(userId, passengerIndex);
        }

        FlightResponse flight = scheduleClient.getFlight(flightId);
        SeatLayout layout = layouts.requireForFlight(flight);
        SeatPricing.PriceList prices = seatPricing.priceList(flight, pricedCabin, pricedFare);
        Set<String> taken = reservationRepository.findAssignedSeats(flightId);
        Set<String> blocked = blockedSeatRepository.findSeatNumbers(flightId);
        Map<String, HoldOwner> held = holds.holdsOnFlight(flightId);

        List<SeatView> seats = new ArrayList<>(layout.seats().size());
        Map<SeatCategory, Integer> availableByCategory = new EnumMap<>(SeatCategory.class);
        for (Seat seat : layout.seats()) {
            HoldOwner holder = held.get(seat.code());
            boolean mine = seat.code().equals(bookedSeat) || owner.equals(holder);
            SeatStatus status = taken.contains(seat.code()) ? SeatStatus.TAKEN
                    : blocked.contains(seat.code()) ? SeatStatus.BLOCKED
                    : holder != null ? SeatStatus.HELD
                    : SeatStatus.AVAILABLE;
            boolean sells = prices.sells(seat);
            BigDecimal price = sells ? prices.price(seat.category()) : null;
            boolean selectable = sells && (status == SeatStatus.AVAILABLE || (status == SeatStatus.HELD && mine));
            if (sells && status == SeatStatus.AVAILABLE) {
                availableByCategory.merge(seat.category(), 1, Integer::sum);
            }
            seats.add(new SeatView(seat.code(), seat.row(), seat.letter(), seat.cabin(), seat.position(),
                    seat.legroom(), seat.features(), seat.category(), status, price, selectable, mine));
        }

        List<PriceBand> legend = prices.rules().keySet().stream()
                .filter(category -> layout.seats().stream()
                        .anyMatch(s -> s.cabin() == pricedCabin && s.category() == category))
                .map(category -> new PriceBand(category, prices.price(category),
                        availableByCategory.getOrDefault(category, 0)))
                .sorted(Comparator.comparing(PriceBand::price).thenComparing(PriceBand::category))
                .toList();

        return new SeatMapResponse(flightId, flight.getFlightNumber(), layout.aircraftTypeId(),
                layout.manufacturer() + " " + layout.model(), layout.body(), layout.wing(), layout.sections(),
                layout.rows(), layout.facilities(), pricedCabin, pricedFare, pricing.currency(),
                prices.surchargePercent(), holds.ttl().toSeconds(), legend, seats);
    }

    private Reservation ownedReservationOnFlight(UUID reservationId, UUID flightId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        CallerAccess.verifyOwnership(reservation);
        if (!reservation.getFlightId().equals(flightId)) {
            throw new SkylineException("This reservation is for another flight", HttpStatus.BAD_REQUEST);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new SkylineException("This reservation is cancelled", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return reservation;
    }
}
