package com.basarsy.skyline.checkin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.checkin.client.ReservationClient;
import com.basarsy.skyline.checkin.client.dto.AirportSummary;
import com.basarsy.skyline.checkin.client.dto.CabinClass;
import com.basarsy.skyline.checkin.client.dto.FlightResponse;
import com.basarsy.skyline.checkin.client.dto.PassengerResponse;
import com.basarsy.skyline.checkin.client.dto.ReservationResponse;
import com.basarsy.skyline.checkin.client.dto.ReservationStatus;
import com.basarsy.skyline.checkin.client.dto.RouteResponse;
import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.dto.CheckInRequest;
import com.basarsy.skyline.checkin.entity.BoardingPass;
import com.basarsy.skyline.checkin.mapper.BoardingPassMapper;
import com.basarsy.skyline.checkin.repository.BoardingPassRepository;
import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CheckInServiceImplTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID FLIGHT_ID = UUID.randomUUID();
    private static final String PNR = "ABC234";

    private final ReservationClient reservationClient = mock(ReservationClient.class);
    private final BoardingPassRepository repository = mock(BoardingPassRepository.class);
    private final BoardingPassMapper mapper = mock(BoardingPassMapper.class);
    private final CheckInServiceImpl service = new CheckInServiceImpl(reservationClient, repository, mapper);

    @BeforeEach
    void setUp() {
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            BoardingPass bp = inv.getArgument(0);
            BoardingPassResponse response = new BoardingPassResponse();
            response.setSeatNumber(bp.getSeatNumber());
            response.setGate(bp.getGate());
            return response;
        });
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanCheckInAndGetsSeatFromReservationService() {
        loginAs(OWNER_ID, "ROLE_PASSENGER");
        ReservationResponse confirmed = reservation(ReservationStatus.CONFIRMED, null);
        when(reservationClient.getReservationByPnr(PNR)).thenReturn(confirmed);
        when(repository.findByReservationId(confirmed.id())).thenReturn(Optional.empty());
        when(reservationClient.checkIn(confirmed.id())).thenReturn(reservation(ReservationStatus.CHECKED_IN, "1A"));

        BoardingPassResponse pass = service.performCheckIn(new CheckInRequest(PNR));

        assertThat(pass.getSeatNumber()).isEqualTo("1A");
        assertThat(pass.getOrigin()).isEqualTo("IST");
        assertThat(pass.getDestination()).isEqualTo("LHR");
        assertThat(pass.getGate()).isEqualTo(CheckInServiceImpl.gateFor(FLIGHT_ID));
    }

    @Test
    void someoneElsesPnrLooksLikeAnUnknownPnr() {
        loginAs(UUID.randomUUID(), "ROLE_PASSENGER");
        when(reservationClient.getReservationByPnr(PNR)).thenReturn(reservation(ReservationStatus.CONFIRMED, null));

        assertThatThrownBy(() -> service.performCheckIn(new CheckInRequest(PNR)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Reservation not found");
        assertThatThrownBy(() -> service.getBoardingPass(PNR))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Reservation not found");
        verify(reservationClient, never()).checkIn(any());
    }

    @Test
    void staffCanCheckInAnyPassenger() {
        loginAs(UUID.randomUUID(), "ROLE_STAFF");
        ReservationResponse confirmed = reservation(ReservationStatus.CONFIRMED, null);
        when(reservationClient.getReservationByPnr(PNR)).thenReturn(confirmed);
        when(repository.findByReservationId(confirmed.id())).thenReturn(Optional.empty());
        when(reservationClient.checkIn(confirmed.id())).thenReturn(reservation(ReservationStatus.CHECKED_IN, "2B"));

        assertThat(service.performCheckIn(new CheckInRequest(PNR)).getSeatNumber()).isEqualTo("2B");
    }

    @Test
    void repeatingCheckInReturnsTheExistingBoardingPass() {
        loginAs(OWNER_ID, "ROLE_PASSENGER");
        ReservationResponse checkedIn = reservation(ReservationStatus.CHECKED_IN, "3C");
        when(reservationClient.getReservationByPnr(PNR)).thenReturn(checkedIn);
        BoardingPass existing = new BoardingPass();
        existing.setSeatNumber("3C");
        when(repository.findByReservationId(checkedIn.id())).thenReturn(Optional.of(existing));

        assertThat(service.performCheckIn(new CheckInRequest(PNR)).getSeatNumber()).isEqualTo("3C");
        verify(reservationClient, never()).checkIn(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void checkedInWithoutBoardingPassIsCompletedOnRetry() {
        loginAs(OWNER_ID, "ROLE_PASSENGER");
        ReservationResponse checkedIn = reservation(ReservationStatus.CHECKED_IN, "4D");
        when(reservationClient.getReservationByPnr(PNR)).thenReturn(checkedIn);
        when(repository.findByReservationId(checkedIn.id())).thenReturn(Optional.empty());
        when(reservationClient.checkIn(checkedIn.id())).thenReturn(checkedIn);

        assertThat(service.performCheckIn(new CheckInRequest(PNR)).getSeatNumber()).isEqualTo("4D");
        verify(repository).saveAndFlush(any());
    }

    @Test
    void gateIsStablePerFlight() {
        assertThat(CheckInServiceImpl.gateFor(FLIGHT_ID)).isEqualTo(CheckInServiceImpl.gateFor(FLIGHT_ID));
        assertThat(CheckInServiceImpl.gateFor(FLIGHT_ID)).matches("G([1-9]|1[0-9]|20)");
    }

    private static final UUID RESERVATION_ID = UUID.randomUUID();

    private static ReservationResponse reservation(ReservationStatus status, String seat) {
        RouteResponse route = new RouteResponse(UUID.randomUUID(),
                new AirportSummary(UUID.randomUUID(), "IST", "Istanbul Airport", "Istanbul", "Turkey"),
                new AirportSummary(UUID.randomUUID(), "LHR", "Heathrow", "London", "UK"), 2500, 240);
        FlightResponse flight = FlightResponse.builder()
                .id(FLIGHT_ID).flightNumber("SK100").route(route)
                .departureTime(LocalDateTime.now().plusHours(24)).build();
        PassengerResponse passenger = new PassengerResponse(UUID.randomUUID(), OWNER_ID, "Ada", "Lovelace",
                "P123", LocalDate.of(1990, 1, 1), "TR");
        return new ReservationResponse(RESERVATION_ID, PNR, passenger, flight, status, seat, CabinClass.ECONOMY,
                BigDecimal.TEN, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }

    private static void loginAs(UUID userId, String role) {
        var principal = new SkylineUserDetails(userId, "user@test.local", null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
