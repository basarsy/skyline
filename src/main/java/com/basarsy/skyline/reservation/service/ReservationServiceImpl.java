package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.SkylineUserDetails;
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
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.repository.UserRepository;
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
    private final FlightRepository flightRepository;
    private final UserRepository userRepository;
    private final ReservationMapper reservationMapper;
    private final PassengerMapper passengerMapper;

    @Override
    @Transactional
    public ReservationResponse bookFlight(ReservationRequest request) {
        Flight flight = flightRepository.findById(request.flightId())
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found"));

        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot book a flight that is not scheduled", HttpStatus.BAD_REQUEST);
        }

        if (flight.getAvailableSeats() <= 0) {
            throw new SkylineException("Flight is fully booked", HttpStatus.BAD_REQUEST);
        }

        // Atomic decrement to handle concurrency
        int updated = flightRepository.decrementSeat(flight.getId());
        if (updated == 0) {
            throw new SkylineException("Flight is fully booked", HttpStatus.BAD_REQUEST);
        }

        User currentUser = getCurrentUser();

        // Check if passenger exists for this user, if not create one, else update
        Passenger passenger = passengerRepository.findByUser(currentUser)
                .orElseGet(() -> {
                    Passenger newPassenger = passengerMapper.toEntity(request.passenger());
                    newPassenger.setUser(currentUser);
                    return newPassenger;
                });

        // Always update passenger details to the latest provided in the request
        if (passenger.getId() != null) {
             passengerMapper.updateEntity(passenger, request.passenger());
        }
        passenger = passengerRepository.save(passenger);

        Reservation reservation = reservationMapper.toEntity(request);
        reservation.setFlight(flight);
        reservation.setPassenger(passenger);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setBookedAt(LocalDateTime.now());
        reservation.setPnr(generatePnr());
        reservation.setTotalPrice(calculatePrice(flight, request.cabinClass()));

        // In a real system, payment processing would happen here, and status would be CONFIRMED upon success
        // We will mock it as CONFIRMED directly for this phase
        reservation.setStatus(ReservationStatus.CONFIRMED);

        Reservation savedReservation = reservationRepository.save(reservation);
        return reservationMapper.toResponse(savedReservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID id) {
        Reservation reservation = getReservationEntity(id);
        verifyOwnership(reservation);
        return reservationMapper.toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationByPnr(String pnr) {
        Reservation reservation = reservationRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with PNR: " + pnr));
        return reservationMapper.toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(Pageable pageable) {
        User currentUser = getCurrentUser();
        Passenger passenger = passengerRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger profile not found"));
        
        return reservationRepository.findByPassenger_Id(passenger.getId(), pageable)
                .map(reservationMapper::toResponse);
    }

    @Override
    @Transactional
    public void cancelReservation(UUID id) {
        Reservation reservation = getReservationEntity(id);
        verifyOwnership(reservation);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new SkylineException("Reservation is already cancelled", HttpStatus.BAD_REQUEST);
        }
        
        if (reservation.getFlight().getStatus() != FlightStatus.SCHEDULED && reservation.getFlight().getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot cancel reservation for a flight that has departed or is boarding", HttpStatus.BAD_REQUEST);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        // Return seat to inventory
        flightRepository.incrementSeat(reservation.getFlight().getId());
    }

    private Reservation getReservationEntity(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SkylineException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        SkylineUserDetails userDetails = (SkylineUserDetails) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void verifyOwnership(Reservation reservation) {
        User currentUser = getCurrentUser();
        // Allow ADMIN or STAFF to view/manage any reservation, or the owner themselves
        if (!currentUser.getId().equals(reservation.getPassenger().getUser().getId()) &&
            currentUser.getRole().name().equals("USER")) {
            throw new SkylineException("You do not have permission to access this reservation", HttpStatus.FORBIDDEN);
        }
    }

    private BigDecimal calculatePrice(Flight flight, CabinClass cabinClass) {
        BigDecimal multiplier = switch (cabinClass) {
            case ECONOMY -> BigDecimal.valueOf(1.0);
            case BUSINESS -> BigDecimal.valueOf(2.5);
            case FIRST -> BigDecimal.valueOf(4.0);
        };
        
        int totalSeats = flight.getAircraft().getAircraftType().getTotalSeats();
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
        // Simple PNR generation using UUID prefix (6 characters)
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return uuid.substring(0, 6);
    }
}
