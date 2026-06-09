package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.FleetClient;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

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

        // Decrement seat via client
        scheduleClient.decrementSeat(flight.getId());

        UUID currentUserId = getCurrentUserId();

        // Check if passenger exists for this user, if not create one, else update
        Passenger passenger = passengerRepository.findByUserId(currentUserId)
                .orElseGet(() -> {
                    Passenger newPassenger = passengerMapper.toEntity(request.passenger());
                    newPassenger.setUserId(currentUserId);
                    return newPassenger;
                });

        // Always update passenger details to the latest provided in the request
        if (passenger.getId() != null) {
             passengerMapper.updateEntity(passenger, request.passenger());
        }
        passenger = passengerRepository.save(passenger);

        Reservation reservation = reservationMapper.toEntity(request);
        reservation.setFlightId(flight.getId());
        reservation.setPassenger(passenger);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setBookedAt(LocalDateTime.now());
        reservation.setPnr(generatePnr());
        reservation.setTotalPrice(calculatePrice(flight, request.cabinClass()));

        // In a real system, payment processing would happen here, and status would be CONFIRMED upon success
        reservation.setStatus(ReservationStatus.CONFIRMED);

        Reservation savedReservation = reservationRepository.save(reservation);
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
    public ReservationResponse getReservationByPnr(String pnr) {
        Reservation reservation = reservationRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with PNR: " + pnr));
        return enrichReservationResponse(reservation, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(Pageable pageable) {
        UUID currentUserId = getCurrentUserId();
        Passenger passenger = passengerRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger profile not found"));
        
        return reservationRepository.findByPassenger_Id(passenger.getId(), pageable)
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
        reservationRepository.save(reservation);

        // Return seat to inventory
        scheduleClient.incrementSeat(reservation.getFlightId());
    }

    @Override
    @Transactional
    public void updateStatus(UUID id, ReservationStatus status, String seatNumber) {
        Reservation reservation = getReservationEntity(id);
        reservation.setStatus(status);
        if (seatNumber != null) {
            reservation.setSeatNumber(seatNumber);
        }
        reservationRepository.save(reservation);
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

    private BigDecimal calculatePrice(FlightResponse flight, CabinClass cabinClass) {
        BigDecimal multiplier = switch (cabinClass) {
            case ECONOMY -> BigDecimal.valueOf(1.0);
            case BUSINESS -> BigDecimal.valueOf(2.5);
            case FIRST -> BigDecimal.valueOf(4.0);
        };
        
        AircraftResponse aircraft = flight.getAircraft();
        if (aircraft == null) {
             aircraft = fleetClient.getAircraft(flight.getAircraft().id()); // Should be id from flight
        }
        // Simplified: use flight's aircraft data if available
        int totalSeats = aircraft.aircraftType().totalSeats();
        int availableSeats = flight.getAvailableSeats();
        double loadFactor = (double) (totalSeats - availableSeats) / totalSeats;
        
        BigDecimal loadFactorMultiplier = BigDecimal.valueOf(1.0);
        if (loadFactor > 0.90) {
            loadFactorMultiplier = BigDecimal.valueOf(1.30);
        } else if (loadFactor > 0.80) {
            loadFactorMultiplier = BigDecimal.valueOf(1.15);
        }

        return flight.getBasePrice().multiply(multiplier).multiply(loadFactorMultiplier);
    }

    private String generatePnr() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return uuid.substring(0, 6);
    }
}
