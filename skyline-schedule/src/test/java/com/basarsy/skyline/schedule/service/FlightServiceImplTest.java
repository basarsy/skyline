package com.basarsy.skyline.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.schedule.client.CrewClient;
import com.basarsy.skyline.schedule.client.FleetClient;
import com.basarsy.skyline.schedule.client.RouteClient;
import com.basarsy.skyline.schedule.client.dto.AircraftResponse;
import com.basarsy.skyline.schedule.client.dto.AircraftStatus;
import com.basarsy.skyline.schedule.client.dto.AircraftTypeSummary;
import com.basarsy.skyline.schedule.client.dto.AirportSummary;
import com.basarsy.skyline.schedule.client.dto.RouteResponse;
import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import com.basarsy.skyline.schedule.mapper.FlightMapperImpl;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class FlightServiceImplTest {

    static final UUID ROUTE_ID = UUID.randomUUID();
    static final UUID AIRCRAFT_ID = UUID.randomUUID();

    final FlightRepository repository = mock(FlightRepository.class);
    final FleetClient fleetClient = mock(FleetClient.class);
    final RouteClient routeClient = mock(RouteClient.class);
    final CrewClient crewClient = mock(CrewClient.class);
    final FlightMapper mapper = new FlightMapperImpl();
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    FlightServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlightServiceImpl(repository, fleetClient, routeClient, crewClient, mapper, events,
                new PricingProperties("EUR"));
        when(routeClient.getRoute(ROUTE_ID)).thenReturn(route());
        when(fleetClient.getAircraft(AIRCRAFT_ID)).thenReturn(aircraft(AIRCRAFT_ID, AircraftStatus.ACTIVE));
        when(repository.save(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void searchReturnsScheduledAndDelayedFlights() {
        when(routeClient.searchRoutes("IST", "LHR")).thenReturn(List.of(route()));
        Flight delayed = flight(FlightStatus.DELAYED);
        when(repository.searchFlights(eq(List.of(ROUTE_ID)), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(delayed)));

        var page = service.searchFlights("IST", "LHR", LocalDate.of(2030, 6, 1), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(f -> f.getStatus()).containsExactly(FlightStatus.DELAYED);
        // 1 June in Istanbul (UTC+3), the origin, as UTC times.
        verify(repository).searchFlights(eq(List.of(ROUTE_ID)), eq(LocalDateTime.of(2030, 5, 31, 21, 0)),
                eq(LocalDateTime.of(2030, 6, 1, 21, 0)),
                eq(java.util.EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED)), any());
    }

    @Test
    void searchTreatsTheDateAsAUtcDayWhenTheOriginZoneIsUnusable() {
        when(routeClient.searchRoutes("IST", "LHR")).thenReturn(List.of(route("Not/AZone")));
        when(repository.searchFlights(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.searchFlights("IST", "LHR", LocalDate.of(2030, 6, 1), PageRequest.of(0, 20));

        verify(repository).searchFlights(eq(List.of(ROUTE_ID)), eq(LocalDateTime.of(2030, 6, 1, 0, 0)),
                eq(LocalDateTime.of(2030, 6, 2, 0, 0)), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void boardEnrichesEachDistinctRouteAndAircraftOnce() {
        Flight first = flight(FlightStatus.SCHEDULED);
        Flight second = flight(FlightStatus.CANCELLED);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        var page = service.getFlightBoard(new FlightBoardFilter(null, null, null, null), PageRequest.of(0, 50));

        assertThat(page.getContent()).allSatisfy(f -> {
            assertThat(f.getRoute().origin().timezone()).isEqualTo("Europe/Istanbul");
            assertThat(f.getAircraft().tailNumber()).isEqualTo("TC-JFA");
        });
        verify(routeClient, times(1)).getRoute(ROUTE_ID);
        verify(fleetClient, times(1)).getAircraft(AIRCRAFT_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUnreachableRouteServiceLeavesTheRouteNullInsteadOfFailing() {
        when(routeClient.getRoute(ROUTE_ID)).thenThrow(new RuntimeException("route service down"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(flight(FlightStatus.SCHEDULED))));

        var page = service.getFlightBoard(new FlightBoardFilter(null, null, null, null), PageRequest.of(0, 50));

        assertThat(page.getContent().get(0).getRoute()).isNull();
        assertThat(page.getContent().get(0).getAircraft()).isNotNull();
    }

    @Test
    void adminCancellingThroughStatusPatchRunsTheFullCancellation() {
        loginAs("ROLE_ADMIN");
        Flight flight = flight(FlightStatus.SCHEDULED);
        flight.setAvailableSeats(120);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        var response = service.updateFlightStatus(flight.getId(), FlightStatus.CANCELLED);

        assertThat(response.getStatus()).isEqualTo(FlightStatus.CANCELLED);
        assertThat(flight.getAvailableSeats()).as("seats reset to capacity").isEqualTo(180);
        verify(events).publishEvent(new FlightCancelledEvent(flight.getId()));
    }

    @Test
    void staffCannotCancelThroughStatusPatch() {
        loginAs("ROLE_STAFF");
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> service.updateFlightStatus(flight.getId(), FlightStatus.CANCELLED))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Only an admin can cancel a flight")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        verifyNoInteractions(events);
    }

    @Test
    void otherStatusChangesStillWorkForStaff() {
        loginAs("ROLE_STAFF");
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThat(service.updateFlightStatus(flight.getId(), FlightStatus.DELAYED).getStatus())
                .isEqualTo(FlightStatus.DELAYED);
        verifyNoInteractions(events);
    }

    @Test
    void anArrivedFlightCannotBeCancelledThroughStatusPatch() {
        loginAs("ROLE_ADMIN");
        Flight flight = flight(FlightStatus.ARRIVED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> service.updateFlightStatus(flight.getId(), FlightStatus.CANCELLED))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(events);
    }

    static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user@skyline.test", null, List.of(new SimpleGrantedAuthority(role))));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsAircraftThatIsNotActive() {
        for (AircraftStatus status : List.of(AircraftStatus.MAINTENANCE, AircraftStatus.RETIRED)) {
            UUID aircraftId = UUID.randomUUID();
            when(fleetClient.getAircraft(aircraftId)).thenReturn(aircraft(aircraftId, status));

            assertThatThrownBy(() -> service.createFlight(request(aircraftId)))
                    .isInstanceOf(SkylineException.class)
                    .hasMessage("Aircraft TC-JFA is not active")
                    .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createAcceptsAnActiveAircraft() {
        var response = service.createFlight(request(AIRCRAFT_ID));

        assertThat(response.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(response.getAvailableSeats()).isEqualTo(180);
        assertThat(response.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void updateRejectsAircraftThatIsNotActive() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        UUID retired = UUID.randomUUID();
        when(fleetClient.getAircraft(retired)).thenReturn(aircraft(retired, AircraftStatus.RETIRED));

        assertThatThrownBy(() -> service.updateFlight(flight.getId(), request(retired)))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Aircraft TC-JFA is not active");
        assertThat(flight.getAircraftId()).isEqualTo(AIRCRAFT_ID);
    }

    @Test
    void updateAcceptsAnActiveAircraft() {
        Flight flight = flight(FlightStatus.DELAYED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        UUID other = UUID.randomUUID();
        when(fleetClient.getAircraft(other)).thenReturn(aircraft(other, AircraftStatus.ACTIVE));

        service.updateFlight(flight.getId(), request(other));

        assertThat(flight.getAircraftId()).isEqualTo(other);
    }

    @Test
    void changingAircraftKeepsExistingBookingsAgainstTheNewCapacity() {
        Flight flight = flight(FlightStatus.SCHEDULED); // 180 seats, 150 available: 30 booked
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        UUID bigger = UUID.randomUUID();
        when(fleetClient.getAircraft(bigger)).thenReturn(aircraft(bigger, AircraftStatus.ACTIVE, 220));

        service.updateFlight(flight.getId(), request(bigger));

        assertThat(flight.getAvailableSeats()).isEqualTo(190);
    }

    @Test
    void keepingTheAircraftLeavesAvailableSeatsAlone() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.updateFlight(flight.getId(), request(AIRCRAFT_ID));

        assertThat(flight.getAvailableSeats()).isEqualTo(150);
    }

    @Test
    void anAircraftTooSmallForTheExistingBookingsIsRefused() {
        Flight flight = flight(FlightStatus.SCHEDULED); // 30 booked
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        UUID small = UUID.randomUUID();
        when(fleetClient.getAircraft(small)).thenReturn(aircraft(small, AircraftStatus.ACTIVE, 20));

        assertThatThrownBy(() -> service.updateFlight(flight.getId(), request(small)))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(flight.getAircraftId()).isEqualTo(AIRCRAFT_ID);
        assertThat(flight.getAvailableSeats()).isEqualTo(150);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void decrementOnlyHoldsSeatsOnBookableFlights() {
        UUID id = UUID.randomUUID();
        when(repository.decrementSeat(eq(id), eq(FlightServiceImpl.BOOKABLE_STATUSES), any())).thenReturn(1);

        service.decrementSeat(id);

        verify(repository).decrementSeat(eq(id), eq(FlightServiceImpl.BOOKABLE_STATUSES), any());
    }

    @Test
    void decrementNamesWhyNoSeatWasHeld() {
        Flight cancelled = flight(FlightStatus.CANCELLED);
        when(repository.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.decrementSeat(cancelled.getId()))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Cannot book a flight in status: CANCELLED");

        Flight full = flight(FlightStatus.SCHEDULED);
        when(repository.findById(full.getId())).thenReturn(Optional.of(full));
        assertThatThrownBy(() -> service.decrementSeat(full.getId()))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Flight is fully booked")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    static FlightRequest request(UUID aircraftId) {
        LocalDateTime departure = LocalDateTime.of(2030, 6, 1, 9, 0);
        return new FlightRequest("SK200", ROUTE_ID, aircraftId, departure, departure.plusHours(4),
                new BigDecimal("120.00"));
    }

    // ------------------------------------------------------------------ fixtures

    static RouteResponse route() {
        return route("Europe/Istanbul");
    }

    static RouteResponse route(String originZone) {
        return new RouteResponse(ROUTE_ID,
                new AirportSummary(UUID.randomUUID(), "IST", "Istanbul Airport", "Istanbul", "Turkey", originZone),
                new AirportSummary(UUID.randomUUID(), "LHR", "Heathrow", "London", "UK", "Europe/London"), 2500, 240);
    }

    static AircraftResponse aircraft(UUID id, AircraftStatus status) {
        return aircraft(id, status, 180);
    }

    static AircraftResponse aircraft(UUID id, AircraftStatus status, int totalSeats) {
        return new AircraftResponse(id, "TC-JFA", new AircraftTypeSummary(UUID.randomUUID(), "Airbus", "A320neo", totalSeats),
                status, 2020);
    }

    static Flight flight(FlightStatus status) {
        Flight flight = new Flight();
        flight.setId(UUID.randomUUID());
        flight.setFlightNumber("SK100");
        flight.setRouteId(ROUTE_ID);
        flight.setAircraftId(AIRCRAFT_ID);
        flight.setDepartureTime(LocalDateTime.of(2030, 6, 1, 9, 0));
        flight.setArrivalTime(LocalDateTime.of(2030, 6, 1, 13, 0));
        flight.setStatus(status);
        flight.setAvailableSeats(150);
        flight.setBasePrice(new BigDecimal("100.00"));
        return flight;
    }
}
