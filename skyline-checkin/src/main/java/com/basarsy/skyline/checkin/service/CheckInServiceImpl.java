package com.basarsy.skyline.checkin.service;

import com.basarsy.skyline.checkin.client.ReservationClient;
import com.basarsy.skyline.checkin.client.dto.FlightResponse;
import com.basarsy.skyline.checkin.client.dto.ReservationResponse;
import com.basarsy.skyline.checkin.client.dto.ReservationStatus;
import com.basarsy.skyline.checkin.client.dto.RouteResponse;
import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.dto.CheckInRequest;
import com.basarsy.skyline.checkin.entity.BoardingPass;
import com.basarsy.skyline.checkin.exception.CheckInWindowException;
import com.basarsy.skyline.checkin.mapper.BoardingPassMapper;
import com.basarsy.skyline.checkin.repository.BoardingPassRepository;
import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private static final Set<String> STAFF_ROLES = Set.of("ROLE_STAFF", "ROLE_ADMIN");
    private static final int GATE_COUNT = 20;

    private final ReservationClient reservationClient;
    private final BoardingPassRepository boardingPassRepository;
    private final BoardingPassMapper boardingPassMapper;
    /** UTC: departure times are UTC, so the window is compared in UTC whatever the host zone. */
    private final Clock clock;

    /**
     * Idempotent: repeating a check-in returns the existing boarding pass. If an earlier attempt marked the
     * reservation CHECKED_IN but failed before the boarding pass was stored, a retry completes it (the
     * reservation-side check-in is idempotent and returns the already assigned seat).
     */
    @Override
    @Transactional
    public BoardingPassResponse performCheckIn(CheckInRequest request) {
        ReservationResponse reservation = findOwnedReservation(request.pnr());

        Optional<BoardingPass> existing = boardingPassRepository.findByReservationId(reservation.id());
        if (existing.isPresent()) {
            return enrichBoardingPassResponse(existing.get(), reservation);
        }

        validateReservationStatus(reservation);
        validateCheckInWindow(reservation);

        ReservationResponse checkedIn = reservationClient.checkIn(reservation.id());

        // saveAndFlush: surface a concurrent duplicate (unique reservation_id) as a 409 here, not at commit.
        BoardingPass boardingPass = boardingPassRepository.saveAndFlush(createBoardingPass(checkedIn));
        return enrichBoardingPassResponse(boardingPass, checkedIn);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardingPassResponse getBoardingPass(String pnr) {
        ReservationResponse reservation = findOwnedReservation(pnr);
        BoardingPass boardingPass = boardingPassRepository.findByReservationId(reservation.id())
                .orElseThrow(() -> new ResourceNotFoundException("Boarding pass not found"));
        return enrichBoardingPassResponse(boardingPass, reservation);
    }

    /**
     * Loads the reservation (as an internal service, so with full data) and enforces that the caller owns it
     * or is STAFF/ADMIN. Someone else's PNR is reported exactly like an unknown PNR.
     */
    private ReservationResponse findOwnedReservation(String pnr) {
        ReservationResponse reservation = reservationClient.getReservationByPnr(pnr);
        if (reservation == null || !callerMayAccess(reservation)) {
            throw new ResourceNotFoundException("Reservation not found");
        }
        if (reservation.flight() == null) {
            throw new SkylineException("Flight details unavailable, please retry", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return reservation;
    }

    static boolean callerMayAccess(ReservationResponse reservation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        if (authentication.getAuthorities().stream().anyMatch(a -> STAFF_ROLES.contains(a.getAuthority()))) {
            return true;
        }
        return authentication.getPrincipal() instanceof SkylineUserDetails user
                && user.getId() != null
                && reservation.passenger() != null
                && user.getId().equals(reservation.passenger().userId());
    }

    private BoardingPassResponse enrichBoardingPassResponse(BoardingPass boardingPass, ReservationResponse reservation) {
        BoardingPassResponse response = boardingPassMapper.toResponse(boardingPass);
        FlightResponse flight = reservation.flight();

        response.setPnr(reservation.pnr());
        response.setPassengerName(reservation.passenger().firstName() + " " + reservation.passenger().lastName());
        response.setFlightNumber(flight.getFlightNumber());
        response.setDepartureTime(flight.getDepartureTime());

        // The flight already embeds its route (schedule enriches it), so no extra call to the route service.
        RouteResponse route = flight.getRoute();
        if (route != null && route.origin() != null && route.destination() != null) {
            response.setOrigin(route.origin().iataCode());
            response.setDestination(route.destination().iataCode());
        }
        return response;
    }

    private void validateCheckInWindow(ReservationResponse reservation) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime departureTime = reservation.flight().getDepartureTime();

        if (now.isBefore(departureTime.minusHours(48))) {
            throw new CheckInWindowException("Check-in opens 48 hours before departure.");
        }
        if (now.isAfter(departureTime.minusHours(1))) {
            throw new CheckInWindowException("Check-in closes 1 hour before departure.");
        }
    }

    private void validateReservationStatus(ReservationResponse reservation) {
        // CHECKED_IN without a boarding pass is the recovery path for an interrupted earlier attempt.
        if (reservation.status() != ReservationStatus.CONFIRMED && reservation.status() != ReservationStatus.CHECKED_IN) {
            throw new CheckInWindowException(
                    "Check-in is only allowed for CONFIRMED reservations. Current status: " + reservation.status());
        }
    }

    private BoardingPass createBoardingPass(ReservationResponse reservation) {
        FlightResponse flight = reservation.flight();
        BoardingPass bp = new BoardingPass();
        bp.setReservationId(reservation.id());
        bp.setSeatNumber(reservation.seatNumber());
        bp.setGate(gateFor(flight.getId()));
        bp.setBoardingTime(flight.getDepartureTime().minusMinutes(45));
        bp.setBarcodeData("SKY-" + reservation.pnr() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        bp.setIssuedAt(LocalDateTime.now(clock));
        return bp;
    }

    /** Placeholder until gates come from operations data: stable per flight, so all passengers agree. */
    static String gateFor(UUID flightId) {
        return "G" + (Math.floorMod(flightId.hashCode(), GATE_COUNT) + 1);
    }
}
