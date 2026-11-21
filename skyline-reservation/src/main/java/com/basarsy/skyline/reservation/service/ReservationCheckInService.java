package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.mapper.ReservationMapper;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.repository.BlockedSeatRepository;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks a reservation in: assigns a seat (if it has none) and marks it CHECKED_IN in one transaction.
 *
 * <p>Seat uniqueness is enforced by the (flight_id, seat_number) unique constraint. Seats are picked at
 * random among the free ones, so concurrent check-ins rarely pick the same seat; if they do, the loser's
 * flush fails, its transaction rolls back, and it retries with a fresh view of the taken seats. The
 * operation is idempotent: an already checked-in reservation is returned unchanged, so callers can
 * safely retry after a partial failure.
 */
@Slf4j
@Service
public class ReservationCheckInService {

    static final int MAX_ATTEMPTS = 3;

    /** Check-in is open while the flight still takes passengers; the same statuses as booking. */
    static final Set<FlightStatus> CHECK_IN_STATUSES = EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    private final ReservationRepository reservationRepository;
    private final ScheduleClient scheduleClient;
    private final ReservationMapper reservationMapper;
    private final TransactionTemplate transactionTemplate;
    private final SeatLayoutProvider layouts;
    private final SeatPricing seatPricing;
    private final SeatHoldStore holds;
    private final BlockedSeatRepository blockedSeats;
    private final SeatSelectionService seatLines;
    private final RandomGenerator random;

    @Autowired
    public ReservationCheckInService(
            ReservationRepository reservationRepository,
            ScheduleClient scheduleClient,
            ReservationMapper reservationMapper,
            PlatformTransactionManager transactionManager,
            SeatLayoutProvider layouts,
            SeatPricing seatPricing,
            SeatHoldStore holds,
            BlockedSeatRepository blockedSeats,
            SeatSelectionService seatLines) {
        this(reservationRepository, scheduleClient, reservationMapper, transactionManager, layouts, seatPricing, holds,
                blockedSeats, seatLines, new SecureRandom());
    }

    ReservationCheckInService(
            ReservationRepository reservationRepository,
            ScheduleClient scheduleClient,
            ReservationMapper reservationMapper,
            PlatformTransactionManager transactionManager,
            SeatLayoutProvider layouts,
            SeatPricing seatPricing,
            SeatHoldStore holds,
            BlockedSeatRepository blockedSeats,
            SeatSelectionService seatLines,
            RandomGenerator random) {
        this.reservationRepository = reservationRepository;
        this.scheduleClient = scheduleClient;
        this.reservationMapper = reservationMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.layouts = layouts;
        this.seatPricing = seatPricing;
        this.holds = holds;
        this.blockedSeats = blockedSeats;
        this.seatLines = seatLines;
        this.random = random;
    }

    public ReservationResponse checkIn(UUID reservationId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> checkInOnce(reservationId));
            } catch (DataIntegrityViolationException seatTaken) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new SkylineException("Could not assign a seat, please retry", HttpStatus.CONFLICT);
                }
                log.info("Seat collision checking in reservation {} (attempt {}), retrying", reservationId, attempt);
            }
        }
    }

    private ReservationResponse checkInOnce(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        FlightResponse flight = scheduleClient.getFlight(reservation.getFlightId());

        // Before the idempotent return: until the cancellation event arrives, a cancelled flight's
        // reservations still look CHECKED_IN here, and must not yield a seat (or boarding pass).
        if (flight == null || !CHECK_IN_STATUSES.contains(flight.getStatus())) {
            throw new SkylineException(
                    "Check-in is closed for this flight. Flight status: " + (flight == null ? null : flight.getStatus()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            return toResponse(reservation, flight);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new SkylineException(
                    "Only CONFIRMED reservations can be checked in. Current status: " + reservation.getStatus(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (reservation.getSeatNumber() == null) {
            reservation.setSeatNumber(assignSeat(reservation, flight));
            seatLines.recordAssignedSeat(reservation);
        }
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        releaseOwnHoldsAfterCommit(reservation);
        return toResponse(reservationRepository.saveAndFlush(reservation), flight);
    }

    /**
     * A seat this passenger held but never confirmed (picked on the check-in page, then pressed "Check in") is not
     * theirs: it may cost money. Once checked in the seat is final, so the hold is let go for others right away
     * instead of lingering until it expires.
     */
    private void releaseOwnHoldsAfterCommit(Reservation reservation) {
        String slot = reservation.getId().toString();
        List<SeatHoldStore.HoldOwner> own = holds.holdsOnFlight(reservation.getFlightId()).values().stream()
                .map(SeatHoldStore.Hold::owner)
                .filter(owner -> owner.slot().equals(slot))
                .distinct()
                .toList();
        if (own.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                own.forEach(owner -> holds.release(reservation.getFlightId(), owner));
            }
        });
    }

    /**
     * A free seat for a passenger who didn't choose one: from the aircraft's layout when it has one (own cabin,
     * free standard seats, close to the rest of the booking), else the legacy 6-abreast numbering.
     */
    private String assignSeat(Reservation reservation, FlightResponse flight) {
        Set<String> taken = reservationRepository.findAssignedSeats(reservation.getFlightId());
        Optional<SeatLayout> layout = layouts.forFlight(flight);
        if (layout.isEmpty()) {
            return SeatMap.randomFree(totalSeats(flight), taken, random).orElseThrow(CheckInSeatErrors::noSeatsLeft);
        }

        Set<String> unavailable = new HashSet<>(taken);
        unavailable.addAll(blockedSeats.findSeatNumbers(flight.getId()));
        holds.holdsOnFlight(flight.getId()).keySet().forEach(unavailable::add);
        List<String> companions = reservation.getBookingGroupId() == null ? List.of()
                : reservationRepository.findByBookingGroupIdAndFlightId(reservation.getBookingGroupId(), flight.getId())
                        .stream()
                        .filter(other -> !other.getId().equals(reservation.getId()))
                        .map(Reservation::getSeatNumber)
                        .filter(Objects::nonNull)
                        .toList();
        SeatPricing.PriceList prices = seatPricing.priceList(flight, reservation.getCabinClass(), reservation.getFareType());
        return SeatAssigner.pick(layout.get(), seat -> seat.cabin() == reservation.getCabinClass(),
                        prices::autoAssignable, unavailable, companions, random)
                .orElseThrow(CheckInSeatErrors::noSeatsLeft);
    }

    private static final class CheckInSeatErrors {
        static SkylineException noSeatsLeft() {
            return new SkylineException("No seats left in your cabin on this flight", HttpStatus.CONFLICT);
        }
    }

    private static int totalSeats(FlightResponse flight) {
        if (flight == null || flight.getAircraft() == null || flight.getAircraft().aircraftType() == null) {
            // Never guess a capacity: a made-up seat map could assign seats that don't exist.
            throw new SkylineException("Seat map unavailable, please retry", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return flight.getAircraft().aircraftType().totalSeats();
    }

    private ReservationResponse toResponse(Reservation reservation, FlightResponse flight) {
        ReservationResponse response = reservationMapper.toResponse(reservation);
        response.setFlight(flight);
        return response;
    }
}
