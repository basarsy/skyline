package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.dto.SeatHoldRequest;
import com.basarsy.skyline.reservation.dto.SeatHoldResponse;
import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationAncillary;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import com.basarsy.skyline.reservation.repository.BlockedSeatRepository;
import com.basarsy.skyline.reservation.repository.ReservationAncillaryRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import com.basarsy.skyline.reservation.service.SeatHoldStore.HoldOwner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Choosing a seat: hold it (Redis, a few minutes, exclusive), then confirm it onto the reservation, where the
 * unique (flight, seat) constraint is the last word. Two passengers racing for one seat: the second hold is a 409,
 * and should two confirmations ever reach the database, the second flush is a 409 too.
 *
 * <p>The seat fee is a line item ({@code SEAT}) priced on the server at confirmation; a seat included in the fare
 * is a zero-price line. A seat can be chosen or changed until check-in (and never after check-in closes, 1h before
 * departure); after that it is final and printed on the boarding pass.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatSelectionService {

    /** Same as the check-in window's closing time. */
    static final long SEAT_CHANGES_CLOSE_HOURS_BEFORE_DEPARTURE = 1;
    private static final Set<FlightStatus> OPEN_STATUSES = EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    private final ScheduleClient scheduleClient;
    private final SeatLayoutProvider layouts;
    private final SeatPricing seatPricing;
    private final SeatHoldStore holds;
    private final ReservationRepository reservationRepository;
    private final ReservationAncillaryRepository ancillaryRepository;
    private final BlockedSeatRepository blockedSeatRepository;
    private final PricingProperties pricing;
    private final Clock clock;

    // --- While booking (no reservation yet) ---

    public SeatHoldResponse holdForBooking(SeatHoldRequest request) {
        HoldOwner owner = HoldOwner.forPassenger(CallerAccess.currentUserId(), request.passengerIndex());
        FlightResponse flight = openFlight(request.flightId());
        FareType fare = request.fareType() == null ? FareType.STANDARD : request.fareType();
        return hold(flight, request.seatNumber(), request.cabinClass(), fare, owner);
    }

    public void releaseForBooking(UUID flightId, int passengerIndex) {
        holds.release(flightId, HoldOwner.forPassenger(CallerAccess.currentUserId(), passengerIndex));
    }

    // --- For an existing reservation (manage booking, check-in) ---

    @Transactional(readOnly = true)
    public SeatHoldResponse holdForReservation(UUID reservationId, String seatNumber) {
        Reservation reservation = changeableReservation(reservationId);
        FlightResponse flight = openFlight(reservation.getFlightId());
        requireBeforeCutOff(flight);
        return hold(flight, seatNumber, reservation.getCabinClass(), reservation.getFareType(),
                HoldOwner.forReservation(CallerAccess.currentUserId(), reservationId));
    }

    public void releaseForReservation(UUID reservationId) {
        Reservation reservation = ownedReservation(reservationId);
        holds.release(reservation.getFlightId(), HoldOwner.forReservation(CallerAccess.currentUserId(), reservationId));
    }

    /** Confirms (and charges) the seat the caller holds for this reservation, replacing its current seat. */
    @Transactional
    public Reservation confirmSeat(UUID reservationId, String seatNumber) {
        Reservation reservation = changeableReservation(reservationId);
        FlightResponse flight = openFlight(reservation.getFlightId());
        requireBeforeCutOff(flight);
        if (seatNumber.equals(reservation.getSeatNumber())) {
            return reservation;
        }
        HoldOwner owner = HoldOwner.forReservation(CallerAccess.currentUserId(), reservationId);
        SeatLayout layout = layouts.requireForFlight(flight);
        SeatPricing.PriceList prices = seatPricing.priceList(flight, reservation.getCabinClass(), reservation.getFareType());
        applyHeldSeat(reservation, flight, layout, prices, seatNumber, owner);
        return reservation;
    }

    /** Gives the seat back; the passenger then gets one at check-in. A paid seat is refunded only on a FLEX fare. */
    @Transactional
    public Reservation removeSeat(UUID reservationId) {
        Reservation reservation = changeableReservation(reservationId);
        FlightResponse flight = openFlight(reservation.getFlightId());
        requireBeforeCutOff(flight);
        endActiveSeatLine(reservation);
        reservation.setSeatNumber(null);
        recalculateTotal(reservation);
        return reservationRepository.saveAndFlush(reservation);
    }

    // --- Shared with booking ---

    /**
     * Puts a seat the owner holds onto the reservation and adds its fee line. Must run inside the booking or
     * seat-change transaction; the hold is released once that commits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void applyHeldSeat(Reservation reservation, FlightResponse flight, SeatLayout layout,
            SeatPricing.PriceList prices, String seatNumber, HoldOwner owner) {
        Seat seat = seatOf(layout, seatNumber);
        BigDecimal currentPrice = prices.priceOf(seat); // also refuses a seat outside the passenger's cabin
        SeatHoldStore.Hold hold = holds.heldBy(flight.getId(), seatNumber, owner)
                .orElseThrow(() -> new SkylineException(
                        "Your hold on seat " + seatNumber + " has expired; please choose a seat again",
                        HttpStatus.CONFLICT));
        // The price shown when the seat was held stands for the hold's lifetime, even if the flight's surcharge
        // changed meanwhile; a hold priced for another cabin or fare is re-priced.
        BigDecimal price = hold.priceFor(reservation.getCabinClass(), reservation.getFareType()).orElse(currentPrice);

        endActiveSeatLine(reservation);
        reservation.setSeatNumber(seatNumber);
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException taken) {
            throw seatUnavailable(seatNumber);
        }

        ReservationAncillary line = new ReservationAncillary();
        line.setReservation(reservation);
        line.setType(AncillaryType.SEAT);
        line.setProductCode("SEAT_" + seat.category());
        line.setDescription("Seat " + seatNumber + " (" + describe(seat) + ")");
        line.setSeatNumber(seatNumber);
        line.setPrice(price);
        line.setChannel(SalesChannel.ONLINE);
        line.setRefundable(reservation.getFareType() == FareType.FLEX);
        ancillaryRepository.saveAndFlush(line);

        recalculateTotal(reservation);
        reservationRepository.saveAndFlush(reservation);
        releaseAfterCommit(flight.getId(), owner);
    }

    /** A seat handed out free at check-in is a zero-price line, so every seated passenger has a seat line. */
    @Transactional(propagation = Propagation.MANDATORY)
    void recordAssignedSeat(Reservation reservation) {
        ReservationAncillary line = new ReservationAncillary();
        line.setReservation(reservation);
        line.setType(AncillaryType.SEAT);
        line.setProductCode("SEAT_ASSIGNED");
        line.setDescription("Seat " + reservation.getSeatNumber() + " (assigned at check-in)");
        line.setSeatNumber(reservation.getSeatNumber());
        line.setPrice(BigDecimal.ZERO.setScale(2));
        line.setRefundable(false);
        ancillaryRepository.save(line);
    }

    /**
     * Total = what the passenger paid and keeps: the fare plus every line not refunded (a seat changed on a
     * non-refundable fare stays paid for).
     */
    void recalculateTotal(Reservation reservation) {
        BigDecimal extras = reservation.getId() == null ? BigDecimal.ZERO : ancillaryRepository.sumKept(reservation.getId());
        reservation.setTotalPrice(reservation.getFarePrice().add(extras));
    }

    // --- Internals ---

    private SeatHoldResponse hold(FlightResponse flight, String seatNumber, CabinClass cabin, FareType fare,
            HoldOwner owner) {
        SeatLayout layout = layouts.requireForFlight(flight);
        Seat seat = seatOf(layout, seatNumber);
        BigDecimal price = seatPricing.priceList(flight, cabin, fare).priceOf(seat); // 422 outside the cabin
        if (reservationRepository.findAssignedSeats(flight.getId()).contains(seatNumber)
                || blockedSeatRepository.findSeatNumbers(flight.getId()).contains(seatNumber)) {
            throw seatUnavailable(seatNumber);
        }
        if (!holds.hold(flight.getId(), seatNumber, owner, cabin, fare, price)) {
            throw seatUnavailable(seatNumber);
        }
        return new SeatHoldResponse(flight.getId(), seatNumber, seat.category(), price, pricing.currency(),
                LocalDateTime.now(clock).plus(holds.ttl()));
    }

    private void endActiveSeatLine(Reservation reservation) {
        if (reservation.getId() == null) {
            return;
        }
        ancillaryRepository.findByReservation_IdAndTypeAndStatus(reservation.getId(), AncillaryType.SEAT,
                        ReservationAncillary.Status.ACTIVE)
                .ifPresent(line -> {
                    line.end();
                    ancillaryRepository.saveAndFlush(line);
                });
    }

    private void releaseAfterCommit(UUID flightId, HoldOwner owner) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    holds.release(flightId, owner);
                } catch (Exception e) {
                    log.warn("Could not release seat hold on flight {} (it expires on its own)", flightId, e);
                }
            }
        });
    }

    private Reservation ownedReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        CallerAccess.verifyOwnership(reservation);
        return reservation;
    }

    private Reservation changeableReservation(UUID reservationId) {
        Reservation reservation = ownedReservation(reservationId);
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            throw new SkylineException("You are checked in: your seat is on your boarding pass and can't be changed",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new SkylineException("Seats can only be chosen on a confirmed booking", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return reservation;
    }

    private FlightResponse openFlight(UUID flightId) {
        FlightResponse flight = scheduleClient.getFlight(flightId);
        if (flight == null || !OPEN_STATUSES.contains(flight.getStatus())) {
            throw new SkylineException("Seat selection is closed for this flight", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return flight;
    }

    private void requireBeforeCutOff(FlightResponse flight) {
        if (!LocalDateTime.now(clock).isBefore(
                flight.getDepartureTime().minusHours(SEAT_CHANGES_CLOSE_HOURS_BEFORE_DEPARTURE))) {
            throw new SkylineException("Seats are final once check-in closes, 1 hour before departure",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    static Seat seatOf(SeatLayout layout, String seatNumber) {
        return layout.seats().stream()
                .filter(seat -> seat.code().equals(seatNumber))
                .findFirst()
                .orElseThrow(() -> new SkylineException("Seat " + seatNumber + " does not exist on this aircraft",
                        HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private static SkylineException seatUnavailable(String seatNumber) {
        return new SkylineException("Seat " + seatNumber + " was just taken by another passenger; the seat map has been "
                + "updated, please choose another", HttpStatus.CONFLICT);
    }

    private static String describe(Seat seat) {
        return seat.position().name().toLowerCase(Locale.ROOT) + ", " + seat.cabin().name().toLowerCase(Locale.ROOT);
    }
}
