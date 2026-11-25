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
import com.basarsy.skyline.common.notification.config.KafkaTopicConfig;
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
import com.basarsy.skyline.schedule.event.FlightChangeType;
import com.basarsy.skyline.schedule.event.FlightChangedEvent;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import com.basarsy.skyline.schedule.mapper.FlightMapperImpl;
import com.basarsy.skyline.common.outbox.Outbox;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.schedule.repository.SeatClaimRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    final SeatClaimRepository seatClaims = mock(SeatClaimRepository.class);
    final FleetClient fleetClient = mock(FleetClient.class);
    final RouteClient routeClient = mock(RouteClient.class);
    final CrewClient crewClient = mock(CrewClient.class);
    final FlightMapper mapper = new FlightMapperImpl();
    final Outbox outbox = mock(Outbox.class);

    FlightServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlightServiceImpl(repository, seatClaims, fleetClient, routeClient, crewClient, mapper, outbox,
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
        verify(outbox).append(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, flight.getId(),
                new FlightCancelledEvent(flight.getId()));
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
        verifyNoInteractions(outbox);
    }

    @Test
    void otherStatusChangesStillWorkForStaff() {
        loginAs("ROLE_STAFF");
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThat(service.updateFlightStatus(flight.getId(), FlightStatus.BOARDING).getStatus())
                .isEqualTo(FlightStatus.BOARDING);
        FlightChangedEvent event = singleChange();
        assertThat(event.type()).isEqualTo(FlightChangeType.STATUS_CHANGED);
        assertThat(event.previousStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(event.status()).isEqualTo(FlightStatus.BOARDING);
    }

    @Test
    void aFlightIsDelayedOnlyWithAnExpectedTime() {
        Flight scheduled = flight(FlightStatus.SCHEDULED);
        Flight boarding = flight(FlightStatus.BOARDING);
        when(repository.findById(scheduled.getId())).thenReturn(Optional.of(scheduled));
        when(repository.findById(boarding.getId())).thenReturn(Optional.of(boarding));

        assertThatThrownBy(() -> service.updateFlightStatus(scheduled.getId(), FlightStatus.DELAYED))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("with its expected departure time");
        assertThatThrownBy(() -> service.updateFlightStatus(boarding.getId(), FlightStatus.DELAYED))
                .isInstanceOf(SkylineException.class);
        assertThat(scheduled.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(boarding.getStatus()).isEqualTo(FlightStatus.BOARDING);
        verifyNoInteractions(outbox);
    }

    @Test
    void settingTheSameStatusAgainAnnouncesNothing() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.updateFlightStatus(flight.getId(), FlightStatus.SCHEDULED);

        verifyNoInteractions(outbox);
    }

    @Test
    void cancellingAlsoAnnouncesTheChangeOnTheChangedTopic() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.cancelFlight(flight.getId());

        verify(outbox).append(KafkaTopicConfig.FLIGHT_CANCELLED_TOPIC, flight.getId(),
                new FlightCancelledEvent(flight.getId()));
        FlightChangedEvent event = singleChange();
        assertThat(event.type()).isEqualTo(FlightChangeType.CANCELLED);
        assertThat(event.origin()).isEqualTo("IST");
        assertThat(event.destination()).isEqualTo("LHR");
        assertThat(event.originTimezone()).isEqualTo("Europe/Istanbul");
        assertThat(event.destinationTimezone()).isEqualTo("Europe/London");
    }

    // ------------------------------------------------------------------ delays and gates

    @Test
    void delayingSetsTheEstimateAndTheStatusAndAnnouncesIt() {
        Flight flight = flight(FlightStatus.SCHEDULED); // 09:00 → 13:00
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        LocalDateTime estimate = LocalDateTime.of(2030, 6, 1, 10, 30);

        var response = service.delayFlight(flight.getId(), estimate);

        assertThat(response.getStatus()).isEqualTo(FlightStatus.DELAYED);
        assertThat(response.getDepartureTime()).as("scheduled time kept").isEqualTo(LocalDateTime.of(2030, 6, 1, 9, 0));
        assertThat(response.getEstimatedDepartureTime()).isEqualTo(estimate);
        assertThat(response.getEstimatedArrivalTime()).isEqualTo(LocalDateTime.of(2030, 6, 1, 14, 30));
        FlightChangedEvent event = singleChange();
        assertThat(event.type()).isEqualTo(FlightChangeType.DELAYED);
        assertThat(event.flightNumber()).isEqualTo("SK100");
        assertThat(event.estimatedDepartureTime()).isEqualTo(estimate);
        assertThat(event.previousEstimatedDepartureTime()).isNull();
        assertThat(event.previousStatus()).isEqualTo(FlightStatus.SCHEDULED);
    }

    @Test
    void aDelayMustBeAfterTheScheduledDepartureAndAtMostADay() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> service.delayFlight(flight.getId(), flight.getDepartureTime()))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Estimated departure must be after the scheduled departure");
        assertThatThrownBy(() -> service.delayFlight(flight.getId(), flight.getDepartureTime().plusHours(25)))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("is a reschedule");
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        verifyNoInteractions(outbox);
    }

    @Test
    void aFlightThatLeftCannotBeDelayed() {
        Flight flight = flight(FlightStatus.DEPARTED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> service.delayFlight(flight.getId(), flight.getDepartureTime().plusHours(1)))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void delayingABoardingFlightKeepsItBoardingSoSalesStayClosed() {
        Flight flight = flight(FlightStatus.BOARDING);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.delayFlight(flight.getId(), flight.getDepartureTime().plusMinutes(40));

        assertThat(flight.getStatus()).isEqualTo(FlightStatus.BOARDING);
        assertThat(flight.getEstimatedDepartureTime()).isEqualTo(flight.getDepartureTime().plusMinutes(40));
        assertThat(singleChange().type()).as("passengers are told").isEqualTo(FlightChangeType.DELAYED);

        service.clearDelay(flight.getId());
        assertThat(flight.getStatus()).as("lifting the delay keeps boarding").isEqualTo(FlightStatus.BOARDING);
    }

    @Test
    void clearingADelayPutsTheFlightBackOnSchedule() {
        Flight flight = delayed(LocalDateTime.of(2030, 6, 1, 11, 0));
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        var response = service.clearDelay(flight.getId());

        assertThat(response.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(response.getEstimatedDepartureTime()).isNull();
        assertThat(response.getEstimatedArrivalTime()).isNull();
        FlightChangedEvent event = singleChange();
        assertThat(event.type()).isEqualTo(FlightChangeType.DELAY_CLEARED);
        assertThat(event.previousEstimatedDepartureTime()).isEqualTo(LocalDateTime.of(2030, 6, 1, 11, 0));
    }

    @Test
    void statusBackToScheduledAlsoLiftsTheDelay() {
        Flight flight = delayed(LocalDateTime.of(2030, 6, 1, 11, 0));
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.updateFlightStatus(flight.getId(), FlightStatus.SCHEDULED);

        assertThat(flight.getEstimatedDepartureTime()).isNull();
        assertThat(singleChange().type()).isEqualTo(FlightChangeType.DELAY_CLEARED);
    }

    @Test
    void boardingADelayedFlightKeepsItsEstimate() {
        Flight flight = delayed(LocalDateTime.of(2030, 6, 1, 11, 0));
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.updateFlightStatus(flight.getId(), FlightStatus.BOARDING);

        assertThat(flight.getEstimatedDepartureTime()).isEqualTo(LocalDateTime.of(2030, 6, 1, 11, 0));
        assertThat(singleChange().type()).isEqualTo(FlightChangeType.STATUS_CHANGED);
    }

    @Test
    void assigningAGateNormalisesItAndAnnouncesTheChange() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        flight.setGate("B4");
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        var response = service.assignGate(flight.getId(), " b12 ");

        assertThat(response.getGate()).isEqualTo("B12");
        FlightChangedEvent event = singleChange();
        assertThat(event.type()).isEqualTo(FlightChangeType.GATE_CHANGED);
        assertThat(event.previousGate()).isEqualTo("B4");
        assertThat(event.gate()).isEqualTo("B12");
    }

    @Test
    void assigningTheSameGateAnnouncesNothing() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        flight.setGate("B12");
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        service.assignGate(flight.getId(), "B12");

        verifyNoInteractions(outbox);
    }

    @Test
    void aDepartedFlightKeepsItsGate() {
        Flight flight = flight(FlightStatus.DEPARTED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> service.assignGate(flight.getId(), "C3"))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Cannot change the gate of a flight in status: DEPARTED");
    }

    @Test
    void reschedulingAnnouncesTheNewTimesAndLiftsTheDelay() {
        Flight flight = delayed(LocalDateTime.of(2030, 6, 1, 11, 0));
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        LocalDateTime newDeparture = LocalDateTime.of(2030, 6, 1, 12, 0);

        service.updateFlight(flight.getId(), new FlightRequest("SK100", ROUTE_ID, AIRCRAFT_ID, newDeparture,
                newDeparture.plusHours(4), new BigDecimal("100.00")));

        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(flight.getEstimatedDepartureTime()).isNull();
        FlightChangedEvent event = singleChange();
        assertThat(event.type()).isEqualTo(FlightChangeType.RESCHEDULED);
        assertThat(event.previousDepartureTime()).isEqualTo(LocalDateTime.of(2030, 6, 1, 9, 0));
        assertThat(event.departureTime()).isEqualTo(newDeparture);
    }

    @Test
    void anUpdateThatKeepsTheTimesAnnouncesNothing() {
        Flight flight = flight(FlightStatus.SCHEDULED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));
        LocalDateTime departure = flight.getDepartureTime();

        service.updateFlight(flight.getId(), new FlightRequest("SK100", ROUTE_ID, AIRCRAFT_ID, departure,
                flight.getArrivalTime(), new BigDecimal("150.00")));

        verifyNoInteractions(outbox);
    }

    /** The one event appended to the changed topic. */
    FlightChangedEvent singleChange() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(eq(KafkaTopicConfig.FLIGHT_CHANGED_TOPIC), any(UUID.class), event.capture());
        return (FlightChangedEvent) event.getValue();
    }

    static Flight delayed(LocalDateTime estimate) {
        Flight flight = flight(FlightStatus.DELAYED);
        flight.setEstimatedDepartureTime(estimate);
        return flight;
    }

    @Test
    void anArrivedFlightCannotBeCancelledThroughStatusPatch() {
        loginAs("ROLE_ADMIN");
        Flight flight = flight(FlightStatus.ARRIVED);
        when(repository.findById(flight.getId())).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> service.updateFlightStatus(flight.getId(), FlightStatus.CANCELLED))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(outbox);
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
        when(repository.decrementSeat(eq(id), eq(FlightServiceImpl.BOOKABLE_STATUSES), any(), any())).thenReturn(1);

        service.decrementSeat(id);

        verify(repository).decrementSeat(eq(id), eq(FlightServiceImpl.BOOKABLE_STATUSES), any(), any());
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

        Flight leaving = flight(FlightStatus.SCHEDULED);
        leaving.setDepartureTime(LocalDateTime.now().plusMinutes(30));
        when(repository.findById(leaving.getId())).thenReturn(Optional.of(leaving));
        assertThatThrownBy(() -> service.decrementSeat(leaving.getId()))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Sales for this flight have closed");
    }

    @Test
    void aClaimHoldsOneSeatHoweverOftenItIsSent() {
        UUID flightId = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        when(seatClaims.insert(claim, flightId)).thenReturn(true, false);
        when(seatClaims.find(claim)).thenReturn(Optional.of(new SeatClaimRepository.Claim(flightId, false)));
        when(repository.decrementSeat(eq(flightId), any(), any(), any())).thenReturn(1);

        service.claimSeat(flightId, claim);
        service.claimSeat(flightId, claim);

        verify(repository, times(1)).decrementSeat(eq(flightId), any(), any(), any());
    }

    @Test
    void aClaimIdAlreadyUsedOnAnotherFlightIsRefused() {
        UUID claim = UUID.randomUUID();
        when(seatClaims.insert(eq(claim), any())).thenReturn(false);
        when(seatClaims.find(claim)).thenReturn(Optional.of(new SeatClaimRepository.Claim(UUID.randomUUID(), false)));

        assertThatThrownBy(() -> service.claimSeat(UUID.randomUUID(), claim))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(repository, org.mockito.Mockito.never()).decrementSeat(any(), any(), any(), any());
    }

    @Test
    void aClaimArrivingAfterItsReleaseTakesNoSeat() {
        UUID flightId = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        when(seatClaims.insert(claim, flightId)).thenReturn(false);
        when(seatClaims.find(claim)).thenReturn(Optional.of(new SeatClaimRepository.Claim(flightId, true)));

        assertThatThrownBy(() -> service.claimSeat(flightId, claim))
                .hasMessage("Seat claim " + claim + " was released");
        verify(repository, org.mockito.Mockito.never()).decrementSeat(any(), any(), any(), any());
    }

    @Test
    void aClaimOnAFullFlightIsRefusedSoItsRowRollsBack() {
        Flight full = flight(FlightStatus.SCHEDULED);
        UUID claim = UUID.randomUUID();
        when(seatClaims.insert(claim, full.getId())).thenReturn(true);
        when(repository.findById(full.getId())).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> service.claimSeat(full.getId(), claim)).hasMessage("Flight is fully booked");
    }

    @Test
    void releasingGivesASeatBackOnlyForAClaimThatHeldOne() {
        UUID flightId = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        when(seatClaims.release(claim, flightId)).thenReturn(true, false);

        service.releaseSeat(flightId, claim);
        service.releaseSeat(flightId, claim);

        verify(repository, times(1)).incrementSeat(eq(flightId), eq(FlightStatus.CANCELLED), any());
    }

    @Test
    void decrementOnlyHoldsSeatsOnFlightsWhoseSalesAreOpen() {
        UUID id = UUID.randomUUID();
        when(repository.decrementSeat(any(), any(), any(), any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        service.decrementSeat(id);

        ArgumentCaptor<LocalDateTime> earliestDeparture = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).decrementSeat(eq(id), any(), earliestDeparture.capture(), any());
        assertThat(earliestDeparture.getValue())
                .isBetween(before.plusHours(1), LocalDateTime.now().plusHours(1));
    }

    @Test
    void searchingAPastDayFindsNothing() {
        when(routeClient.searchRoutes("IST", "LHR")).thenReturn(List.of(route("UTC")));

        var page = service.searchFlights("IST", "LHR", LocalDate.now().minusDays(2), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verify(repository, org.mockito.Mockito.never()).searchFlights(any(), any(), any(), any(), any());
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
