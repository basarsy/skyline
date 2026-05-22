package com.basarsy.skyline.schedule.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.route.entity.Route;
import com.basarsy.skyline.route.repository.RouteRepository;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final RouteRepository routeRepository;
    private final AircraftRepository aircraftRepository;
    private final FlightMapper flightMapper;

    @Override
    @Transactional
    public FlightResponse createFlight(FlightRequest request) {
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        Aircraft aircraft = aircraftRepository.findById(request.aircraftId())
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found"));

        if (request.departureTime().isAfter(request.arrivalTime()) || request.departureTime().isEqual(request.arrivalTime())) {
            throw new SkylineException("Departure time must be before arrival time", HttpStatus.BAD_REQUEST);
        }

        Flight flight = flightMapper.toEntity(request);
        flight.setRoute(route);
        flight.setAircraft(aircraft);
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setAvailableSeats(aircraft.getAircraftType().getTotalSeats());

        Flight savedFlight = flightRepository.save(flight);
        return flightMapper.toResponse(savedFlight);
    }

    @Override
    @Transactional
    public FlightResponse updateFlight(UUID id, FlightRequest request) {
        Flight flight = getFlightEntity(id);

        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot update flight in status: " + flight.getStatus(), HttpStatus.BAD_REQUEST);
        }

        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        Aircraft aircraft = aircraftRepository.findById(request.aircraftId())
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found"));

        if (request.departureTime().isAfter(request.arrivalTime()) || request.departureTime().isEqual(request.arrivalTime())) {
            throw new SkylineException("Departure time must be before arrival time", HttpStatus.BAD_REQUEST);
        }

        flightMapper.updateEntity(flight, request);
        flight.setRoute(route);
        flight.setAircraft(aircraft);
        // We do not modify availableSeats directly on update to prevent messing up existing bookings
        // A real system would have complex logic for aircraft changes affecting capacity

        return flightMapper.toResponse(flightRepository.save(flight));
    }

    @Override
    @Transactional
    public FlightResponse updateFlightStatus(UUID id, FlightStatus status) {
        Flight flight = getFlightEntity(id);

        // Basic state machine validation
        if (!isValidStatusTransition(flight.getStatus(), status)) {
            throw new SkylineException("Invalid status transition from " + flight.getStatus() + " to " + status, HttpStatus.BAD_REQUEST);
        }

        flight.setStatus(status);
        return flightMapper.toResponse(flightRepository.save(flight));
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlight(UUID id) {
        return flightMapper.toResponse(getFlightEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> searchFlights(String origin, String destination, LocalDate date, Pageable pageable) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        Page<Flight> flights = flightRepository.searchFlights(
                origin, destination, startOfDay, endOfDay, FlightStatus.SCHEDULED, pageable);
                
        return flights.map(flightMapper::toResponse);
    }

    @Override
    @Transactional
    public void cancelFlight(UUID id) {
        Flight flight = getFlightEntity(id);
        
        if (flight.getStatus() == FlightStatus.CANCELLED || flight.getStatus() == FlightStatus.DEPARTED || flight.getStatus() == FlightStatus.ARRIVED) {
            throw new SkylineException("Cannot cancel flight in status: " + flight.getStatus(), HttpStatus.BAD_REQUEST);
        }

        flight.setStatus(FlightStatus.CANCELLED);
        flightRepository.save(flight);
        
        // TODO: Cancellation cascade logic (Phase 2)
    }

    private Flight getFlightEntity(UUID id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + id));
    }

    private boolean isValidStatusTransition(FlightStatus current, FlightStatus next) {
        if (current == next) return true;
        
        return switch (current) {
            case SCHEDULED -> next == FlightStatus.BOARDING || next == FlightStatus.DELAYED || next == FlightStatus.CANCELLED;
            case DELAYED -> next == FlightStatus.BOARDING || next == FlightStatus.CANCELLED || next == FlightStatus.SCHEDULED;
            case BOARDING -> next == FlightStatus.DEPARTED || next == FlightStatus.DELAYED || next == FlightStatus.CANCELLED;
            case DEPARTED -> next == FlightStatus.ARRIVED;
            case ARRIVED, CANCELLED -> false; // Terminal states
        };
    }
}
