package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.FleetClient;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.dto.PassengerResponse;
import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.Passenger;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.mapper.PassengerMapper;
import com.basarsy.skyline.reservation.mapper.ReservationMapper;
import com.basarsy.skyline.reservation.repository.PassengerRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final String PNR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PNR_LENGTH = 6;
    private static final int PNR_MAX_ATTEMPTS = 5;
    private static final SecureRandom PNR_RANDOM = new SecureRandom();
    private static final java.util.Set<String> PRIVILEGED_ROLES = java.util.Set.of("ROLE_STAFF", "ROLE_ADMIN", "ROLE_SERVICE");

    private final ReservationRepository reservationRepository;
    private final PassengerRepository passengerRepository;
    private final ScheduleClient scheduleClient;
    private final FleetClient fleetClient;
    private final ReservationMapper reservationMapper;
    private final PassengerMapper passengerMapper;

    @Override
    @Transactional
    public ReservationResponse bookFlight(ReservationRequest request) {
        FlightResponse flight = scheduleClient.getFlight(request.flightId());

        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot book a flight that is not scheduled", HttpStatus.BAD_REQUEST);
        }

        if (flight.getAvailableSeats() <= 0) {
            throw new SkylineException("Flight is fully booked", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = getCurrentUserId();

        // The seat lives in the schedule service, so it can't join this transaction. Hold it remotely,
        // then give it back if this transaction does not commit for ANY reason, including failures at
        // commit time (e.g. a constraint violation on flush), which a try/catch here can never see.
        scheduleClient.decrementSeat(flight.getId());
        releaseSeatUnlessCommitted(flight.getId());

        // Each booking gets its own passenger snapshot. Reusing one passenger per user meant that
        // booking for someone else rewrote the name/passport on all of the user's past reservations.
        Passenger passenger = passengerMapper.toEntity(request.passenger());
        passenger.setUserId(currentUserId);
        passenger = passengerRepository.save(passenger);

        Reservation reservation = reservationMapper.toEntity(request);
        reservation.setFlightId(flight.getId());
        reservation.setPassenger(passenger);
        reservation.setBookedAt(LocalDateTime.now());
        reservation.setPnr(generateUniquePnr());
        reservation.setTotalPrice(calculatePrice(flight, request.cabinClass()));

        // In a real system, payment processing would happen here, and status would be CONFIRMED upon success
        reservation.setStatus(ReservationStatus.CONFIRMED);

        Reservation savedReservation = reservationRepository.saveAndFlush(reservation);
        return enrichReservationResponse(savedReservation, flight);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID id) {
        Reservation reservation = getReservationEntity(id);
        verifyOwnership(reservation);
        return enrichReservationResponse(reservation, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationByPnr(String pnr, String lastName) {
        Reservation reservation = reservationRepository.findByPnr(pnr.trim().toUpperCase())
                .orElseThrow(ReservationServiceImpl::pnrNotFound);

        // Staff, internal services and the booking owner see the full reservation.
        if (isPrivilegedCaller() || isOwner(reservation)) {
            return enrichReservationResponse(reservation, null);
        }

        // Anyone else needs PNR + last name ("manage booking") and gets a view without sensitive
        // identity data. A wrong last name is indistinguishable from a non-existent PNR, so the
        // endpoint cannot be used to confirm which PNRs exist.
        if (lastName != null && lastName.trim().equalsIgnoreCase(reservation.getPassenger().getLastName().trim())) {
            ReservationResponse response = enrichReservationResponse(reservation, null);
            response.setPassenger(redacted(response.getPassenger()));
            return response;
        }
        throw pnrNotFound();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(Pageable pageable) {
        // A user with no bookings yet gets an empty page, not a 404.
        return reservationRepository.findByPassenger_UserId(getCurrentUserId(), pageable)
                .map(r -> enrichReservationResponse(r, null));
    }

    @Override
    @Transactional
    public void cancelReservation(UUID id) {
        Reservation reservation = getReservationEntity(id);
        verifyOwnership(reservation);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new SkylineException("Reservation is already cancelled", HttpStatus.BAD_REQUEST);
        }
        
        FlightResponse flight = scheduleClient.getFlight(reservation.getFlightId());
        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot cancel reservation for a flight that has departed or is boarding", HttpStatus.BAD_REQUEST);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        // Free the seat number so it can be assigned again ((flight_id, seat_number) is unique).
        reservation.setSeatNumber(null);
        reservationRepository.save(reservation);

        // Return the seat only once the cancellation is durable. Releasing it before commit could
        // oversell the flight if the commit then failed.
        releaseSeatAfterCommit(reservation.getFlightId());
    }

    private ReservationResponse enrichReservationResponse(Reservation reservation, FlightResponse flight) {
        ReservationResponse response = reservationMapper.toResponse(reservation);
        if (flight == null) {
            try {
                flight = scheduleClient.getFlight(reservation.getFlightId());
            } catch (Exception e) {
                // Log error
            }
        }
        response.setFlight(flight);
        return response;
    }

    private Reservation getReservationEntity(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SkylineException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        com.basarsy.skyline.common.security.SkylineUserDetails userDetails = (com.basarsy.skyline.common.security.SkylineUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }

    private void verifyOwnership(Reservation reservation) {
        UUID currentUserId = getCurrentUserId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isStaffOrAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));

        if (!isStaffOrAdmin && !currentUserId.equals(reservation.getPassenger().getUserId())) {
            throw new SkylineException("You do not have permission to access this reservation", HttpStatus.FORBIDDEN);
        }
    }

    private static boolean isPrivilegedCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> PRIVILEGED_ROLES.contains(a.getAuthority()));
    }

    private static boolean isOwner(Reservation reservation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof com.basarsy.skyline.common.security.SkylineUserDetails user
                && user.getId() != null
                && user.getId().equals(reservation.getPassenger().getUserId());
    }

    private static ResourceNotFoundException pnrNotFound() {
        return new ResourceNotFoundException("Reservation not found");
    }

    /** Passenger view for unauthenticated PNR + last name lookups: no DOB, no owner id, masked passport. */
    static PassengerResponse redacted(PassengerResponse passenger) {
        if (passenger == null) {
            return null;
        }
        String passport = passenger.passportNumber();
        String masked = passport == null || passport.length() <= 3
                ? "***"
                : "*".repeat(passport.length() - 3) + passport.substring(passport.length() - 3);
        return new PassengerResponse(passenger.id(), null, passenger.firstName(), passenger.lastName(), masked,
                null, passenger.nationality());
    }

    private BigDecimal calculatePrice(FlightResponse flight, CabinClass cabinClass) {
        BigDecimal multiplier = switch (cabinClass) {
            case ECONOMY -> BigDecimal.valueOf(1.0);
            case BUSINESS -> BigDecimal.valueOf(2.5);
            case FIRST -> BigDecimal.valueOf(4.0);
        };
        
        AircraftResponse aircraft = flight.getAircraft();
        int totalSeats = (aircraft != null && aircraft.aircraftType() != null)
                ? aircraft.aircraftType().totalSeats()
                : Math.max(flight.getAvailableSeats(), 180); // Default to standard capacity if aircraft not enriched
        
        int availableSeats = flight.getAvailableSeats();
        double loadFactor = totalSeats > 0 ? (double) (totalSeats - availableSeats) / totalSeats : 0.0;
        
        BigDecimal loadFactorMultiplier = BigDecimal.valueOf(1.0);
        if (loadFactor > 0.90) {
            loadFactorMultiplier = BigDecimal.valueOf(1.30);
        } else if (loadFactor > 0.80) {
            loadFactorMultiplier = BigDecimal.valueOf(1.15);
        }

        return flight.getBasePrice().multiply(multiplier).multiply(loadFactorMultiplier);
    }

    private void releaseSeatUnlessCommitted(UUID flightId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    releaseSeat(flightId, "booking transaction did not commit");
                }
            }
        });
    }

    private void releaseSeatAfterCommit(UUID flightId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                releaseSeat(flightId, "reservation cancelled");
            }
        });
    }

    private void releaseSeat(UUID flightId, String reason) {
        try {
            scheduleClient.incrementSeat(flightId);
        } catch (Exception ex) {
            // Runs after the transaction finished, so there is nothing left to roll back. Log loudly with a
            // stable marker so the seat count can be reconciled (alerting / a retry job can key off it).
            log.error("SEAT_RELEASE_FAILED flightId={} reason={}: seat inventory needs reconciliation",
                    flightId, reason, ex);
        }
    }

    /**
     * 6-character PNR from SecureRandom over 32 unambiguous characters (no 0/O, 1/I): about 1.07 billion
     * values instead of 16.7 million hex values from a non-cryptographic source, with a collision check.
     */
    private String generateUniquePnr() {
        for (int attempt = 0; attempt < PNR_MAX_ATTEMPTS; attempt++) {
            StringBuilder pnr = new StringBuilder(PNR_LENGTH);
            for (int i = 0; i < PNR_LENGTH; i++) {
                pnr.append(PNR_ALPHABET.charAt(PNR_RANDOM.nextInt(PNR_ALPHABET.length())));
            }
            String candidate = pnr.toString();
            if (!reservationRepository.existsByPnr(candidate)) {
                return candidate;
            }
        }
        throw new SkylineException("Could not allocate a booking reference, please retry", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
