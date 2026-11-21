package com.basarsy.skyline.reservation;

import static com.basarsy.skyline.reservation.SeatTestFixtures.actAs;
import static com.basarsy.skyline.reservation.SeatTestFixtures.passenger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.FleetClient;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.dto.GroupReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.service.ReservationCheckInService;
import com.basarsy.skyline.reservation.service.ReservationService;
import com.basarsy.skyline.reservation.service.SeatSelectionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Check-in assigns seats from the real layout, price table and taken seats, and seats are final afterwards. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CheckInSeatAssignmentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @MockitoBean
    ScheduleClient scheduleClient;

    @MockitoBean
    FleetClient fleetClient;

    @Autowired
    ReservationService reservations;

    @Autowired
    ReservationCheckInService checkIn;

    @Autowired
    SeatSelectionService seats;

    private final UUID flightId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(scheduleClient.getFlight(flightId)).thenReturn(SeatTestFixtures.flight(flightId));
        when(fleetClient.getSeatLayout(any())).thenReturn(SeatTestFixtures.layout());
        actAs(alice);
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passengersWithoutASeatGetAFreeStandardSeatInTheirCabinNextToEachOther() {
        List<ReservationResponse> family = book(CabinClass.ECONOMY, "Ada", "Alan", "Grace");

        List<String> assigned = family.stream().map(r -> checkIn.checkIn(r.getId()).getSeatNumber()).toList();

        assertThat(assigned).doesNotHaveDuplicates()
                .allMatch(seat -> seat.matches("[35][A-F]")); // never Business (1), bulkhead (2) or exit row (4)
        assertThat(assigned).extracting(seat -> seat.charAt(0)).containsOnly(assigned.get(0).charAt(0)); // one row
        ReservationResponse first = reservations.getReservation(family.get(0).getId());
        assertThat(first.getAncillaries()).singleElement()
                .satisfies(line -> assertThat(line.productCode()).isEqualTo("SEAT_ASSIGNED"));
        assertThat(first.getTotalPrice()).isEqualByComparingTo(first.getFarePrice());
    }

    @Test
    void businessPassengersAreSeatedInBusiness() {
        ReservationResponse booked = book(CabinClass.BUSINESS, "Ada").get(0);

        assertThat(checkIn.checkIn(booked.getId()).getSeatNumber()).startsWith("1");
    }

    @Test
    void aChosenSeatIsKeptAndIsFinalAfterCheckIn() {
        ReservationResponse booked = book(CabinClass.ECONOMY, "Ada").get(0);
        seats.holdForReservation(booked.getId(), "4F");
        seats.confirmSeat(booked.getId(), "4F");

        assertThat(checkIn.checkIn(booked.getId()).getSeatNumber()).isEqualTo("4F");
        assertThatThrownBy(() -> seats.holdForReservation(booked.getId(), "5A"))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("checked in")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private List<ReservationResponse> book(CabinClass cabin, String... names) {
        return reservations.bookGroup(new GroupReservationRequest(
                java.util.Arrays.stream(names).map(SeatTestFixtures::passenger).toList(),
                List.of(new GroupReservationRequest.Leg(flightId, cabin, FareType.STANDARD)),
                List.of())).reservations();
    }
}
