package com.basarsy.skyline.schedule.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.schedule.client.FleetClient;
import com.basarsy.skyline.schedule.client.RouteClient;
import com.basarsy.skyline.schedule.client.CrewClient;
import com.basarsy.skyline.schedule.client.dto.AircraftResponse;
import com.basarsy.skyline.schedule.client.dto.RouteResponse;
import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.schedule.repository.FlightSpecifications;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    /** Statuses a passenger can book (see the reservation service), so the ones search must return. */
    static final Set<FlightStatus> BOOKABLE_STATUSES = EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    private final FlightRepository flightRepository;
    private final FleetClient fleetClient;
    private final RouteClient routeClient;
    private final CrewClient crewClient;
    private final FlightMapper flightMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public FlightResponse createFlight(FlightRequest request) {
        RouteResponse route = routeClient.getRoute(request.routeId());
        
        AircraftResponse aircraft = fleetClient.getAircraft(request.aircraftId());

        if (request.departureTime().isAfter(request.arrivalTime()) || request.departureTime().isEqual(request.arrivalTime())) {
            throw new SkylineException("Departure time must be before arrival time", HttpStatus.BAD_REQUEST);
        }

        Flight flight = flightMapper.toEntity(request);
        flight.setRouteId(route.id());
        flight.setAircraftId(aircraft.id());
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setAvailableSeats(aircraft.aircraftType().totalSeats());

        Flight savedFlight = flightRepository.save(flight);
        return enrichFlightResponse(savedFlight);
    }

    @Override
    @Transactional
    public FlightResponse updateFlight(UUID id, FlightRequest request) {
        Flight flight = getFlightEntity(id);

        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot update flight in status: " + flight.getStatus(), HttpStatus.BAD_REQUEST);
        }

        RouteResponse route = routeClient.getRoute(request.routeId());
        
        AircraftResponse aircraft = fleetClient.getAircraft(request.aircraftId());

        if (request.departureTime().isAfter(request.arrivalTime()) || request.departureTime().isEqual(request.arrivalTime())) {
            throw new SkylineException("Departure time must be before arrival time", HttpStatus.BAD_REQUEST);
        }

        flightMapper.updateEntity(flight, request);
        flight.setRouteId(route.id());
        flight.setAircraftId(aircraft.id());
        // We do not modify availableSeats directly on update to prevent messing up existing bookings

        return enrichFlightResponse(flightRepository.save(flight));
    }

    @Override
    @Transactional
    public FlightResponse updateFlightStatus(UUID id, FlightStatus status) {
        Flight flight = getFlightEntity(id);

        // Basic state machine validation
        if (!isValidStatusTransition(flight.getStatus(), status)) {
            throw new SkylineException("Invalid status transition from " + flight.getStatus() + " to " + status, HttpStatus.BAD_REQUEST);
        }

        // Crew validation for operational statuses
        if (status == FlightStatus.BOARDING || status == FlightStatus.DEPARTED) {
            crewClient.validateCrewForFlight(id);
        }

        flight.setStatus(status);
        return enrichFlightResponse(flightRepository.save(flight));
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlight(UUID id) {
        return enrichFlightResponse(getFlightEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> searchFlights(String origin, String destination, LocalDate date, Pageable pageable) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        List<UUID> matchingRouteIds = routeClient.searchRoutes(origin, destination).stream()
                .map(RouteResponse::id)
                .toList();

        if (matchingRouteIds.isEmpty()) {
            return Page.empty(pageable);
        }
        
        Page<Flight> flights = flightRepository.searchFlights(
                matchingRouteIds, startOfDay, endOfDay, BOOKABLE_STATUSES, pageable);
                
        return enrichPage(flights);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> getFlightBoard(FlightBoardFilter filter, Pageable pageable) {
        return enrichPage(flightRepository.findAll(FlightSpecifications.matching(filter), pageable));
    }

    /** Enriches a page, fetching each distinct route and aircraft once instead of once per flight. */
    private Page<FlightResponse> enrichPage(Page<Flight> flights) {
        Map<UUID, RouteResponse> routes = new HashMap<>();
        Map<UUID, AircraftResponse> aircraft = new HashMap<>();
        return flights.map(flight -> enrich(flight,
                id -> routes.computeIfAbsent(id, this::fetchRoute),
                id -> aircraft.computeIfAbsent(id, this::fetchAircraft)));
    }

    private FlightResponse enrichFlightResponse(Flight flight) {
        return enrich(flight, this::fetchRoute, this::fetchAircraft);
    }

    private FlightResponse enrich(Flight flight, Function<UUID, RouteResponse> routes,
            Function<UUID, AircraftResponse> aircraft) {
        FlightResponse res = flightMapper.toResponse(flight);
        // Null when the other service is unreachable: clients render a fallback rather than failing the read.
        res.setRoute(routes.apply(flight.getRouteId()));
        res.setAircraft(aircraft.apply(flight.getAircraftId()));
        return res;
    }

    private RouteResponse fetchRoute(UUID routeId) {
        try {
            return routeClient.getRoute(routeId);
        } catch (Exception e) {
            log.warn("Could not load route {} to enrich a flight: {}", routeId, e.getMessage());
            return null;
        }
    }

    private AircraftResponse fetchAircraft(UUID aircraftId) {
        try {
            return fleetClient.getAircraft(aircraftId);
        } catch (Exception e) {
            log.warn("Could not load aircraft {} to enrich a flight: {}", aircraftId, e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public void cancelFlight(UUID id) {
        Flight flight = getFlightEntity(id);

        if (flight.getStatus() == FlightStatus.CANCELLED || flight.getStatus() == FlightStatus.DEPARTED || flight.getStatus() == FlightStatus.ARRIVED) {
            throw new SkylineException("Cannot cancel flight in status: " + flight.getStatus(), HttpStatus.BAD_REQUEST);
        }

        flight.setStatus(FlightStatus.CANCELLED);
        
        // Enrich to get seat count for reset
        AircraftResponse aircraft = fleetClient.getAircraft(flight.getAircraftId());
        flight.setAvailableSeats(aircraft.aircraftType().totalSeats());
        
        flightRepository.save(flight);

        eventPublisher.publishEvent(new FlightCancelledEvent(id));
    }
    @Override
    @Transactional
    public void decrementSeat(UUID id) {
        int updated = flightRepository.decrementSeat(id);
        if (updated == 0) {
            throw new SkylineException("Flight is fully booked", HttpStatus.CONFLICT);
        }
    }

    @Override
    @Transactional
    public void incrementSeat(UUID id) {
        flightRepository.incrementSeat(id);
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
