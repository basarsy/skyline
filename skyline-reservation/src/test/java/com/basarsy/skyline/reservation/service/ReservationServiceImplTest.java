package com.basarsy.skyline.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.reservation.client.FleetClient;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.dto.PassengerRequest;
import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.Passenger;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.mapper.PassengerMapper;
import com.basarsy.skyline.reservation.mapper.ReservationMapper;
import com.basarsy.skyline.reservation.repository.PassengerRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Drives the transaction-synchronization callbacks by hand (as Spring's transaction manager would) to
 * prove seats are returned exactly when the reservation transaction does not commit.
 */
class ReservationServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FLIGHT_ID = UUID.randomUUID();

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final PassengerRepository passengerRepository = mock(PassengerRepository.class);
    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final ReservationMapper reservationMapper = mock(ReservationMapper.class);
    private final PassengerMapper passengerMapper = mock(PassengerMapper.class);

    private final ReservationServiceImpl service = new ReservationServiceImpl(
            reservationRepository, passengerRepository, scheduleClient, mock(FleetClient.class),
            reservationMapper, passengerMapper);

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        var principal = new SkylineUserDetails(USER_ID, "pax@test.local", null,
                List.of(new SimpleGrantedAuthority("ROLE_PASSENGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        FlightResponse flight = FlightResponse.builder()
                .id(FLIGHT_ID).status(FlightStatus.SCHEDULED).availableSeats(100).basePrice(BigDecimal.TEN).build();
        when(scheduleClient.getFlight(FLIGHT_ID)).thenReturn(flight);
        when(passengerMapper.toEntity(any())).thenAnswer(inv -> new Passenger());
        when(passengerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationMapper.toEntity(any())).thenAnswer(inv -> {
            Reservation r = new Reservation();
            r.setCabinClass(CabinClass.ECONOMY);
            return r;
        });
        when(reservationRepository.existsByPnr(anyString())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        SecurityContextHolder.clearContext();
    }

    @Test
    void bookingThatFailsToPersistReleasesTheHeldSeat() {
        when(reservationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.bookFlight(request())).isInstanceOf(DataIntegrityViolationException.class);
        verify(scheduleClient).decrementSeat(FLIGHT_ID);
        verify(scheduleClient, never()).incrementSeat(any());

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(scheduleClient).incrementSeat(FLIGHT_ID);
    }

    @Test
    void bookingThatCommitsKeepsTheSeat() {
        when(reservationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationMapper.toResponse(any())).thenReturn(new com.basarsy.skyline.reservation.dto.ReservationResponse());

        service.bookFlight(request());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(scheduleClient).decrementSeat(FLIGHT_ID);
        verify(scheduleClient, never()).incrementSeat(any());
    }

    @Test
    void bookingGetsAnUnambiguousSixCharacterPnr() {
        when(reservationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationMapper.toResponse(any())).thenReturn(new com.basarsy.skyline.reservation.dto.ReservationResponse());
        var saved = org.mockito.ArgumentCaptor.forClass(Reservation.class);

        service.bookFlight(request());

        verify(reservationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPnr()).matches("[A-HJ-NP-Z2-9]{6}");
    }

    @Test
    void cancellationReleasesTheSeatOnlyAfterCommit() {
        Reservation reservation = existingReservation();
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        service.cancelReservation(reservation.getId());
        verify(scheduleClient, never()).incrementSeat(any());

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(scheduleClient).incrementSeat(FLIGHT_ID);
    }

    @Test
    void cancellationThatRollsBackDoesNotReleaseTheSeat() {
        Reservation reservation = existingReservation();
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        service.cancelReservation(reservation.getId());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(scheduleClient, never()).incrementSeat(any());
    }

    private static ReservationRequest request() {
        return new ReservationRequest(FLIGHT_ID,
                new PassengerRequest("Ada", "Lovelace", "P123", LocalDate.of(1990, 1, 1), "TR"), CabinClass.ECONOMY);
    }

    private static Reservation existingReservation() {
        Passenger passenger = new Passenger();
        passenger.setUserId(USER_ID);
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setFlightId(FLIGHT_ID);
        reservation.setPassenger(passenger);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservation;
    }

    /** What Spring's transaction manager does at the end of a transaction. */
    private static void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(s -> s.afterCompletion(status));
    }
}
