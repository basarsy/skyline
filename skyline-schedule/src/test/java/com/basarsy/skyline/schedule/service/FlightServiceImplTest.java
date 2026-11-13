package com.basarsy.skyline.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.schedule.client.CrewClient;
import com.basarsy.skyline.schedule.client.FleetClient;
import com.basarsy.skyline.schedule.client.RouteClient;
import com.basarsy.skyline.schedule.client.dto.AircraftResponse;
import com.basarsy.skyline.schedule.client.dto.AircraftStatus;
import com.basarsy.skyline.schedule.client.dto.AircraftTypeSummary;
import com.basarsy.skyline.schedule.client.dto.AirportSummary;
import com.basarsy.skyline.schedule.client.dto.RouteResponse;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import com.basarsy.skyline.schedule.mapper.FlightMapperImpl;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
        service = new FlightServiceImpl(repository, fleetClient, routeClient, crewClient, mapper, events);
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
        verify(repository).searchFlights(eq(List.of(ROUTE_ID)), eq(LocalDate.of(2030, 6, 1).atStartOfDay()), any(),
                eq(java.util.EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED)), any());
    }

    // ------------------------------------------------------------------ fixtures

    static RouteResponse route() {
        return new RouteResponse(ROUTE_ID,
                new AirportSummary(UUID.randomUUID(), "IST", "Istanbul Airport", "Istanbul", "Turkey", "Europe/Istanbul"),
                new AirportSummary(UUID.randomUUID(), "LHR", "Heathrow", "London", "UK", "Europe/London"), 2500, 240);
    }

    static AircraftResponse aircraft(UUID id, AircraftStatus status) {
        return new AircraftResponse(id, "TC-JFA", new AircraftTypeSummary(UUID.randomUUID(), "Airbus", "A320neo", 180),
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
