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
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks a reservation in: assigns a seat (if it has none) and marks it CHECKED_IN in one transaction.
 *
 * <p>Seat uniqueness is enforced by the (flight_id, seat_number) unique constraint. Two concurrent
 * check-ins can pick the same free seat; the loser's flush fails, its transaction rolls back, and it
 * retries with a fresh view of the taken seats. The operation is idempotent: an already checked-in
 * reservation is returned unchanged, so callers can safely retry after a partial failure.
 */
@Slf4j
@Service
public class ReservationCheckInService {

    static final int MAX_ATTEMPTS = 3;

    private final ReservationRepository reservationRepository;
    private final ScheduleClient scheduleClient;
    private final ReservationMapper reservationMapper;
    private final TransactionTemplate transactionTemplate;

    public ReservationCheckInService(
            ReservationRepository reservationRepository,
            ScheduleClient scheduleClient,
            ReservationMapper reservationMapper,
            PlatformTransactionManager transactionManager) {
        this.reservationRepository = reservationRepository;
        this.scheduleClient = scheduleClient;
        this.reservationMapper = reservationMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            return toResponse(reservation, flight);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new SkylineException(
                    "Only CONFIRMED reservations can be checked in. Current status: " + reservation.getStatus(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (reservation.getSeatNumber() == null) {
            String seat = SeatMap.firstFree(totalSeats(flight), reservationRepository.findAssignedSeats(reservation.getFlightId()))
                    .orElseThrow(() -> new SkylineException("No seats left on this flight", HttpStatus.CONFLICT));
            reservation.setSeatNumber(seat);
        }
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        return toResponse(reservationRepository.saveAndFlush(reservation), flight);
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
