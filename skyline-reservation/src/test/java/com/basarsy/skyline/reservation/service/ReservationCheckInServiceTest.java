package com.basarsy.skyline.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.AircraftTypeSummary;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.mapper.ReservationMapper;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

class ReservationCheckInServiceTest {

    private static final UUID FLIGHT_ID = UUID.randomUUID();

    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final ReservationMapper mapper = mock(ReservationMapper.class);
    private final ReservationCheckInService service =
            new ReservationCheckInService(repository, scheduleClient, mapper, mock(PlatformTransactionManager.class));

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setFlightId(FLIGHT_ID);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(repository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(scheduleClient.getFlight(FLIGHT_ID)).thenReturn(flightWithSeats(12));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            ReservationResponse response = new ReservationResponse();
            response.setSeatNumber(r.getSeatNumber());
            response.setStatus(r.getStatus());
            return response;
        });
    }

    @Test
    void assignsFirstFreeSeatAndChecksIn() {
        when(repository.findAssignedSeats(FLIGHT_ID)).thenReturn(Set.of("1A", "1B"));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = service.checkIn(reservation.getId());

        assertThat(response.getSeatNumber()).isEqualTo("1C");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test
    void retriesWithFreshSeatsWhenAConcurrentCheckInTookTheSeat() {
        when(repository.findAssignedSeats(FLIGHT_ID)).thenReturn(Set.of(), Set.of("1A"));
        when(repository.saveAndFlush(any()))
                .thenAnswer(inv -> {
                    // Simulate the rollback of the failed attempt: the entity is reloaded fresh next time.
                    reservation.setSeatNumber(null);
                    reservation.setStatus(ReservationStatus.CONFIRMED);
                    throw new DataIntegrityViolationException("uq_reservations_flight_seat");
                })
                .thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = service.checkIn(reservation.getId());

        assertThat(response.getSeatNumber()).isEqualTo("1B");
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void givesUpWithConflictAfterMaxAttempts() {
        when(repository.findAssignedSeats(FLIGHT_ID)).thenReturn(Set.of());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            reservation.setSeatNumber(null);
            reservation.setStatus(ReservationStatus.CONFIRMED);
            throw new DataIntegrityViolationException("uq_reservations_flight_seat");
        });

        assertThatThrownBy(() -> service.checkIn(reservation.getId()))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, times(ReservationCheckInService.MAX_ATTEMPTS)).saveAndFlush(any());
    }

    @Test
    void isIdempotentForAlreadyCheckedInReservations() {
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setSeatNumber("4D");

        ReservationResponse response = service.checkIn(reservation.getId());

        assertThat(response.getSeatNumber()).isEqualTo("4D");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCancelledReservations() {
        reservation.setStatus(ReservationStatus.CANCELLED);

        assertThatThrownBy(() -> service.checkIn(reservation.getId()))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void reportsFullFlight() {
        when(scheduleClient.getFlight(FLIGHT_ID)).thenReturn(flightWithSeats(2));
        when(repository.findAssignedSeats(FLIGHT_ID)).thenReturn(Set.of("1A", "1B"));

        assertThatThrownBy(() -> service.checkIn(reservation.getId()))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void refusesToGuessSeatMapWhenAircraftIsUnknown() {
        when(scheduleClient.getFlight(FLIGHT_ID)).thenReturn(FlightResponse.builder().id(FLIGHT_ID).build());

        assertThatThrownBy(() -> service.checkIn(reservation.getId()))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void seatMapIsRowMajorSixAbreastAndBoundedByCapacity() {
        assertThat(SeatMap.seatAt(0)).isEqualTo("1A");
        assertThat(SeatMap.seatAt(5)).isEqualTo("1F");
        assertThat(SeatMap.seatAt(6)).isEqualTo("2A");
        assertThat(SeatMap.seatAt(179)).isEqualTo("30F");
        assertThat(SeatMap.firstFree(7, Set.of("1A", "1B", "1C", "1D", "1E", "1F"))).contains("2A");
        assertThat(SeatMap.firstFree(6, Set.of("1A", "1B", "1C", "1D", "1E", "1F"))).isEmpty();
    }

    private static FlightResponse flightWithSeats(int totalSeats) {
        AircraftResponse aircraft = new AircraftResponse(
                UUID.randomUUID(), "TC-TEST", new AircraftTypeSummary(UUID.randomUUID(), "Airbus", "A320", totalSeats),
                null, 2020);
        return FlightResponse.builder().id(FLIGHT_ID).aircraft(aircraft).build();
    }
}
