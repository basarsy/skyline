package com.basarsy.skyline.schedule.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.schedule.client.FleetClient;
import com.basarsy.skyline.schedule.client.RouteClient;
import com.basarsy.skyline.schedule.client.CrewClient;
import com.basarsy.skyline.schedule.client.dto.AircraftResponse;
import com.basarsy.skyline.schedule.client.dto.AircraftStatus;
import com.basarsy.skyline.schedule.client.dto.AirportSummary;
import com.basarsy.skyline.schedule.client.dto.RouteResponse;
import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import com.basarsy.skyline.common.outbox.Outbox;
import com.basarsy.skyline.schedule.repository.AircraftLock;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.schedule.repository.SeatClaimRepository;
import com.basarsy.skyline.schedule.repository.FlightSpecifications;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import com.basarsy.skyline.schedule.event.FlightChangeType;
import com.basarsy.skyline.schedule.event.FlightChangedEvent;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    /** Statuses a passenger can book (see the reservation service), so the ones search must return. */
    static final Set<FlightStatus> BOOKABLE_STATUSES = EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    /** Statuses in which ops can set or lift a delay: the flight has not left yet. */
    static final Set<FlightStatus> DELAYABLE_STATUSES =
            EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED, FlightStatus.BOARDING);

    /** A longer delay is a reschedule: move the flight's times instead. */
    static final Duration MAX_DELAY = Duration.ofHours(24);

    /**
     * Sales close this long before the scheduled departure, when check-in and online add-ons close too (all three
     * go by the scheduled time), so every seat sold can still be checked in. Search stops listing the flight then.
     */
    static final Duration SALES_CLOSE_BEFORE = Duration.ofHours(1);

    /** Least time an aircraft spends on the ground between two flights (deplane, clean, refuel, board). */
    static final Duration MIN_TURNAROUND = Duration.ofMinutes(30);

    /** Flights that no longer occupy their aircraft. */
    static final Set<FlightStatus> ENDED_STATUSES = EnumSet.of(FlightStatus.CANCELLED, FlightStatus.ARRIVED);

    /** Stands in for "no flight" when a new flight is checked against the others. */
    private static final UUID NO_FLIGHT = new UUID(0, 0);

    private final FlightRepository flightRepository;
    private final SeatClaimRepository seatClaims;
    private final AircraftLock aircraftLock;
    private final FleetClient fleetClient;
    private final RouteClient routeClient;
    private final CrewClient crewClient;
    private final FlightMapper flightMapper;
    private final Outbox outbox;
    private final PricingProperties pricing;

    @Override
    @Transactional
    public FlightResponse createFlight(FlightRequest request) {
        RouteResponse route = routeClient.getRoute(request.routeId());
        
        AircraftResponse aircraft = requireActive(fleetClient.getAircraft(request.aircraftId()));

        if (request.departureTime().isAfter(request.arrivalTime()) || request.departureTime().isEqual(request.arrivalTime())) {
            throw new SkylineException("Departure time must be before arrival time", HttpStatus.BAD_REQUEST);
        }
        requireAircraftFree(aircraft, NO_FLIGHT, request.departureTime(), request.arrivalTime());

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
        
        AircraftResponse aircraft = requireActive(fleetClient.getAircraft(request.aircraftId()));

        if (request.departureTime().isAfter(request.arrivalTime()) || request.departureTime().isEqual(request.arrivalTime())) {
            throw new SkylineException("Departure time must be before arrival time", HttpStatus.BAD_REQUEST);
        }
        requireAircraftFree(aircraft, id, request.departureTime(), request.arrivalTime());

        if (!aircraft.id().equals(flight.getAircraftId())) {
            flight.setAvailableSeats(seatsOnNewAircraft(flight, aircraft));
        }

        Snapshot before = Snapshot.of(flight);
        flightMapper.updateEntity(flight, request);
        flight.setRouteId(route.id());
        flight.setAircraftId(aircraft.id());
        if (before.timesDifferFrom(flight)) {
            // The new times are the plan now: a delay against the old ones no longer applies.
            liftDelay(flight);
        }

        return saveAndAnnounce(flight, before);
    }

    @Override
    @Transactional
    public FlightResponse delayFlight(UUID id, LocalDateTime estimatedDepartureTime) {
        Flight flight = getFlightEntity(id);
        if (!DELAYABLE_STATUSES.contains(flight.getStatus())) {
            throw new SkylineException("Cannot delay a flight in status: " + flight.getStatus(), HttpStatus.BAD_REQUEST);
        }
        if (!estimatedDepartureTime.isAfter(flight.getDepartureTime())) {
            throw new SkylineException("Estimated departure must be after the scheduled departure",
                    HttpStatus.BAD_REQUEST);
        }
        if (estimatedDepartureTime.isAfter(flight.getDepartureTime().plus(MAX_DELAY))) {
            throw new SkylineException("A delay of more than " + MAX_DELAY.toHours()
                    + " hours is a reschedule: change the flight's times instead", HttpStatus.BAD_REQUEST);
        }

        Snapshot before = Snapshot.of(flight);
        flight.setEstimatedDepartureTime(estimatedDepartureTime);
        // A boarding flight stays BOARDING with its new expected time: back to DELAYED would reopen sales and
        // lose the boarding state when the delay is lifted. Passengers are told of the delay either way.
        if (flight.getStatus() != FlightStatus.BOARDING) {
            flight.setStatus(FlightStatus.DELAYED);
        }
        return saveAndAnnounce(flight, before);
    }

    @Override
    @Transactional
    public FlightResponse clearDelay(UUID id) {
        Flight flight = getFlightEntity(id);
        if (!DELAYABLE_STATUSES.contains(flight.getStatus())) {
            throw new SkylineException("Cannot change the delay of a flight in status: " + flight.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }
        Snapshot before = Snapshot.of(flight);
        liftDelay(flight);
        return saveAndAnnounce(flight, before);
    }

    @Override
    @Transactional
    public FlightResponse assignGate(UUID id, String gate) {
        Flight flight = getFlightEntity(id);
        if (flight.getStatus() == FlightStatus.DEPARTED || flight.getStatus() == FlightStatus.ARRIVED
                || flight.getStatus() == FlightStatus.CANCELLED) {
            throw new SkylineException("Cannot change the gate of a flight in status: " + flight.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }
        Snapshot before = Snapshot.of(flight);
        flight.setGate(gate.trim().toUpperCase(Locale.ROOT));
        return saveAndAnnounce(flight, before);
    }

    /**
     * An aircraft flies one flight at a time, with {@link #MIN_TURNAROUND} on the ground between two: refused when
     * another of its flights (other than {@code flightId}, not cancelled or arrived) overlaps. Takes the
     * aircraft's lock first, so a concurrent booking of the same aircraft waits for this transaction.
     */
    private void requireAircraftFree(AircraftResponse aircraft, UUID flightId, LocalDateTime departure,
            LocalDateTime arrival) {
        aircraftLock.lock(aircraft.id());
        List<String> conflicts = flightRepository.aircraftConflicts(aircraft.id(), flightId, ENDED_STATUSES,
                departure.minus(MIN_TURNAROUND), arrival.plus(MIN_TURNAROUND), PageRequest.of(0, 1));
        if (!conflicts.isEmpty()) {
            throw new SkylineException("Aircraft " + aircraft.tailNumber() + " already flies " + conflicts.get(0)
                    + " then (it needs " + MIN_TURNAROUND.toMinutes() + " minutes on the ground between flights)",
                    HttpStatus.CONFLICT);
        }
    }

    /** Back on schedule: no expected time, and a DELAYED flight is SCHEDULED again (BOARDING stays). */
    private static void liftDelay(Flight flight) {
        flight.setEstimatedDepartureTime(null);
        if (flight.getStatus() == FlightStatus.DELAYED) {
            flight.setStatus(FlightStatus.SCHEDULED);
        }
    }

    @Override
    @Transactional
    public FlightResponse updateFlightStatus(UUID id, FlightStatus status) {
        if (status == FlightStatus.CANCELLED) {
            // Same rule and same effect as DELETE /flights/{id}: seats reset, bookings cancelled via Kafka.
            if (!callerIsAdmin()) {
                throw new SkylineException("Only an admin can cancel a flight", HttpStatus.FORBIDDEN);
            }
            cancelFlight(id);
            return enrichFlightResponse(getFlightEntity(id));
        }

        Flight flight = getFlightEntity(id);

        if (status == FlightStatus.DELAYED && flight.getStatus() != FlightStatus.DELAYED) {
            // A delay without an expected time tells passengers nothing: it goes through PUT /flights/{id}/delay.
            throw new SkylineException("Delay a flight with its expected departure time (PUT /flights/" + id
                    + "/delay)", HttpStatus.BAD_REQUEST);
        }
        // Basic state machine validation
        if (!isValidStatusTransition(flight.getStatus(), status)) {
            throw new SkylineException("Invalid status transition from " + flight.getStatus() + " to " + status, HttpStatus.BAD_REQUEST);
        }

        // Crew validation for operational statuses
        if (status == FlightStatus.BOARDING || status == FlightStatus.DEPARTED) {
            crewClient.validateCrewForFlight(id);
        }

        Snapshot before = Snapshot.of(flight);
        flight.setStatus(status);
        if (before.status() == FlightStatus.DELAYED && status == FlightStatus.SCHEDULED) {
            liftDelay(flight);
        }
        return saveAndAnnounce(flight, before);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlight(UUID id) {
        return enrichFlightResponse(getFlightEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> searchFlights(String origin, String destination, LocalDate date, Pageable pageable) {
        List<RouteResponse> routes = routeClient.searchRoutes(origin, destination);
        if (routes.isEmpty()) {
            return Page.empty(pageable);
        }

        // `date` is a calendar day at the origin airport. Every matching route leaves from that one airport.
        AirportSummary originAirport = routes.get(0).origin();
        DepartureDay day = DepartureDay.of(date, originAirport == null ? null : originAirport.timezone());
        List<UUID> routeIds = routes.stream().map(RouteResponse::id).toList();
        // Today's flights whose sales have closed are not offered (the day's window starts no earlier than that).
        LocalDateTime salesOpen = LocalDateTime.now().plus(SALES_CLOSE_BEFORE);
        LocalDateTime from = day.start().isBefore(salesOpen) ? salesOpen : day.start();
        if (!from.isBefore(day.end())) {
            return Page.empty(pageable);
        }

        return enrichPage(flightRepository.searchFlights(routeIds, from, day.end(), BOOKABLE_STATUSES, pageable));
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
        res.setCurrency(pricing.currency());
        if (flight.getEstimatedDepartureTime() != null) {
            // Expected to land as late as it leaves.
            Duration delay = Duration.between(flight.getDepartureTime(), flight.getEstimatedDepartureTime());
            res.setEstimatedArrivalTime(flight.getArrivalTime().plus(delay));
        }
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

        Snapshot before = Snapshot.of(flight);
        flight.setStatus(FlightStatus.CANCELLED);
        
        // Enrich to get seat count for reset
        AircraftResponse aircraft = fleetClient.getAircraft(flight.getAircraftId());
        flight.setAvailableSeats(aircraft.aircraftType().totalSeats());

        flightRepository.save(flight);
        seatClaims.releaseAllOnFlight(id);

        // Same transaction as the status change: the reservation service hears of it only if the cancellation
        // commits, and then at least once (the outbox publisher sends it after commit, off the request thread).
        outbox.append(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, id, new FlightCancelledEvent(id));
        announce(before, flight, fetchRoute(flight.getRouteId()));
    }

    /** Saves the flight, then records an event for each change since {@code before} (same transaction). */
    private FlightResponse saveAndAnnounce(Flight flight, Snapshot before) {
        FlightResponse response = enrichFlightResponse(flightRepository.save(flight));
        announce(before, flight, response.getRoute());
        return response;
    }

    /**
     * Appends the {@link FlightChangedEvent}s that describe how {@code flight} differs from {@code before}: at
     * most one for the schedule or status (cancelled, rescheduled, delayed, delay lifted, or another status
     * change, in that order of precedence) and one for the gate. Nothing is appended when nothing changed.
     */
    private void announce(Snapshot before, Flight flight, RouteResponse route) {
        FlightChangeType type = null;
        if (flight.getStatus() == FlightStatus.CANCELLED && before.status() != FlightStatus.CANCELLED) {
            type = FlightChangeType.CANCELLED;
        } else if (before.timesDifferFrom(flight)) {
            type = FlightChangeType.RESCHEDULED;
        } else if (!Objects.equals(before.estimatedDepartureTime(), flight.getEstimatedDepartureTime())) {
            type = flight.getEstimatedDepartureTime() != null
                    ? FlightChangeType.DELAYED : FlightChangeType.DELAY_CLEARED;
        } else if (before.status() != flight.getStatus()) {
            type = FlightChangeType.STATUS_CHANGED;
        }
        if (type != null) {
            append(type, before, flight, route);
        }
        if (!Objects.equals(before.gate(), flight.getGate())) {
            append(FlightChangeType.GATE_CHANGED, before, flight, route);
        }
    }

    private void append(FlightChangeType type, Snapshot before, Flight flight, RouteResponse route) {
        FlightChangedEvent event = new FlightChangedEvent(type, flight.getId(), flight.getFlightNumber(),
                route == null || route.origin() == null ? null : route.origin().iataCode(),
                route == null || route.destination() == null ? null : route.destination().iataCode(),
                route == null || route.origin() == null ? null : route.origin().timezone(),
                route == null || route.destination() == null ? null : route.destination().timezone(),
                flight.getStatus(), before.status(),
                flight.getDepartureTime(), before.departureTime(), flight.getArrivalTime(),
                flight.getEstimatedDepartureTime(), before.estimatedDepartureTime(),
                flight.getGate(), before.gate(), LocalDateTime.now());
        outbox.append(KafkaTopicConfig.FLIGHT_CHANGED_TOPIC, flight.getId(), event);
    }

    /** What a flight looked like before an operation, to tell what the operation changed. */
    private record Snapshot(FlightStatus status, LocalDateTime departureTime, LocalDateTime arrivalTime,
            LocalDateTime estimatedDepartureTime, String gate) {

        static Snapshot of(Flight flight) {
            return new Snapshot(flight.getStatus(), flight.getDepartureTime(), flight.getArrivalTime(),
                    flight.getEstimatedDepartureTime(), flight.getGate());
        }

        boolean timesDifferFrom(Flight flight) {
            return !departureTime.equals(flight.getDepartureTime()) || !arrivalTime.equals(flight.getArrivalTime());
        }
    }
    @Override
    @Transactional
    public void decrementSeat(UUID id) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliestDeparture = now.plus(SALES_CLOSE_BEFORE);
        int updated = flightRepository.decrementSeat(id, BOOKABLE_STATUSES, earliestDeparture, now);
        if (updated == 0) {
            // Nothing matched: say why (unknown flight, not bookable any more, sales closed, or simply full).
            Flight flight = getFlightEntity(id);
            if (!BOOKABLE_STATUSES.contains(flight.getStatus())) {
                throw new SkylineException("Cannot book a flight in status: " + flight.getStatus(), HttpStatus.CONFLICT);
            }
            if (flight.getDepartureTime().isBefore(earliestDeparture)) {
                throw new SkylineException("Sales for this flight have closed", HttpStatus.CONFLICT);
            }
            throw new SkylineException("Flight is fully booked", HttpStatus.CONFLICT);
        }
    }

    @Override
    @Transactional
    public void incrementSeat(UUID id) {
        flightRepository.incrementSeat(id, FlightStatus.CANCELLED, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> flightsOnAircraft(UUID aircraftId, int limit) {
        return flightRepository.flightsOnAircraft(aircraftId, ENDED_STATUSES, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void claimSeat(UUID flightId, UUID claimId) {
        if (!seatClaims.insert(claimId, flightId)) {
            // Rows are never deleted, so a conflicting id is always found.
            SeatClaimRepository.Claim claim = seatClaims.find(claimId).orElseThrow();
            if (!claim.flightId().equals(flightId)) {
                throw new SkylineException("Seat claim " + claimId + " is for another flight", HttpStatus.CONFLICT);
            }
            if (claim.released()) {
                // Its booking gave up (the release overtook this call): taking a seat now would leak it.
                throw new SkylineException("Seat claim " + claimId + " was released", HttpStatus.CONFLICT);
            }
            return; // Already holds its seat: a repeated call.
        }
        decrementSeat(flightId); // Refused (full, closed, not bookable): the claim rolls back with it.
    }

    @Override
    @Transactional
    public void releaseSeat(UUID flightId, UUID claimId) {
        if (seatClaims.release(claimId, flightId)) {
            incrementSeat(flightId);
        }
    }

    /**
     * Seats left after moving a flight to another aircraft: the bookings it already holds stay, so the new
     * capacity minus those bookings. Refused when the new aircraft can't seat everyone already booked.
     */
    private int seatsOnNewAircraft(Flight flight, AircraftResponse newAircraft) {
        int booked = fleetClient.getAircraft(flight.getAircraftId()).aircraftType().totalSeats() - flight.getAvailableSeats();
        int available = newAircraft.aircraftType().totalSeats() - booked;
        if (available < 0) {
            throw new SkylineException("Aircraft " + newAircraft.tailNumber() + " has fewer seats than the "
                    + booked + " already booked on this flight", HttpStatus.CONFLICT);
        }
        return available;
    }

    /** Aircraft in maintenance or retired can't be scheduled. */
    private static AircraftResponse requireActive(AircraftResponse aircraft) {
        if (aircraft.status() != AircraftStatus.ACTIVE) {
            throw new SkylineException("Aircraft " + aircraft.tailNumber() + " is not active", HttpStatus.BAD_REQUEST);
        }
        return aircraft;
    }

    private static boolean callerIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Flight getFlightEntity(UUID id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id: " + id));
    }

    private boolean isValidStatusTransition(FlightStatus current, FlightStatus next) {
        if (current == next) return true;
        
        return switch (current) {
            // DELAYED is entered only through delayFlight (it needs an expected time), and BOARDING is one-way:
            // a delay while boarding keeps BOARDING (see delayFlight), so sales never reopen.
            case SCHEDULED -> next == FlightStatus.BOARDING || next == FlightStatus.CANCELLED;
            case DELAYED -> next == FlightStatus.BOARDING || next == FlightStatus.CANCELLED || next == FlightStatus.SCHEDULED;
            case BOARDING -> next == FlightStatus.DEPARTED || next == FlightStatus.CANCELLED;
            case DEPARTED -> next == FlightStatus.ARRIVED;
            case ARRIVED, CANCELLED -> false; // Terminal states
        };
    }
}
