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
import com.basarsy.skyline.reservation.dto.GroupReservationResponse;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.dto.SeatHoldRequest;
import com.basarsy.skyline.reservation.dto.SeatMapResponse;
import com.basarsy.skyline.reservation.dto.SeatMapResponse.SeatStatus;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.service.ReservationService;
import com.basarsy.skyline.reservation.service.SeatMapService;
import com.basarsy.skyline.reservation.service.SeatSelectionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
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

/**
 * Seat selection against real PostgreSQL (unique seat per flight, migrations, price table) and Redis (holds with
 * TTL). Holds last 2 seconds here (application-test.yml) so expiry can be observed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SeatSelectionIntegrationTest {

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
    SeatSelectionService seats;

    @Autowired
    SeatMapService seatMaps;

    @Autowired
    ReservationService reservations;

    private final UUID flightId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(scheduleClient.getFlight(flightId)).thenReturn(SeatTestFixtures.flight(flightId));
        when(fleetClient.getSeatLayout(any())).thenReturn(SeatTestFixtures.layout());
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void twoUsersPickingTheSameSeatAtOnceOnlyOneGetsIt() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpStatus>> results = new ArrayList<>();
            for (UUID user : List.of(alice, bob)) {
                Callable<HttpStatus> pick = () -> {
                    actAs(user);
                    start.await();
                    try {
                        seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY));
                        return HttpStatus.CREATED;
                    } catch (SkylineException e) {
                        return e.getStatus();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                results.add(pool.submit(pick));
            }
            start.countDown();
            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<HttpStatus> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theLoserSeesTheSeatHeldOnARefreshedMapAndTheWinnerSeesItAsTheirs() {
        actAs(alice);
        seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY));
        assertThat(seatOf(seatMaps.getSeatMap(flightId, null, CabinClass.ECONOMY, FareType.STANDARD, 0), "3A"))
                .satisfies(seat -> {
                    assertThat(seat.status()).isEqualTo(SeatStatus.HELD);
                    assertThat(seat.mine()).isTrue();
                    assertThat(seat.selectable()).isTrue();
                });

        actAs(bob);
        assertThatThrownBy(() -> seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY)))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(seatOf(seatMaps.getSeatMap(flightId, null, CabinClass.ECONOMY, FareType.STANDARD, 0), "3A"))
                .satisfies(seat -> {
                    assertThat(seat.status()).isEqualTo(SeatStatus.HELD);
                    assertThat(seat.mine()).isFalse();
                    assertThat(seat.selectable()).isFalse();
                });
    }

    @Test
    void anExpiredHoldFreesTheSeat() {
        actAs(alice);
        seats.holdForBooking(hold("5A", 0, CabinClass.ECONOMY));

        actAs(bob);
        // A 409 until Alice's hold lapses. Same thread: the caller's security context is thread-bound.
        Awaitility.await().pollInSameThread().ignoreException(SkylineException.class)
                .atMost(Duration.ofSeconds(6)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                assertThat(seats.holdForBooking(hold("5A", 0, CabinClass.ECONOMY)).seatNumber()).isEqualTo("5A"));
    }

    @Test
    void choosingAnotherSeatReleasesThePreviousOne() {
        actAs(alice);
        seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY));
        seats.holdForBooking(hold("3F", 0, CabinClass.ECONOMY));

        actAs(bob);
        assertThat(seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY)).seatNumber()).isEqualTo("3A");
    }

    @Test
    void aPassengerCanOnlyPickASeatInTheirOwnCabin() {
        actAs(alice);
        assertThatThrownBy(() -> seats.holdForBooking(hold("1A", 0, CabinClass.ECONOMY)))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("you can only choose a seat in ECONOMY")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        SeatMapResponse map = seatMaps.getSeatMap(flightId, null, CabinClass.ECONOMY, FareType.STANDARD, 0);
        assertThat(seatOf(map, "1A").selectable()).isFalse();
        assertThat(seatOf(map, "1A").price()).isNull();
    }

    @Test
    void aGroupBookingChargesHeldSeatsAsLineItemsAndTakesThem() {
        actAs(alice);
        seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY)); // window, PREFERRED: 6.00 on STANDARD
        seats.holdForBooking(hold("3B", 1, CabinClass.ECONOMY)); // middle, STANDARD: included

        GroupReservationResponse booked = reservations.bookGroup(new GroupReservationRequest(
                List.of(passenger("Ada"), passenger("Alan")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.ECONOMY, FareType.STANDARD)),
                List.of(new GroupReservationRequest.SeatSelection(0, flightId, "3A"),
                        new GroupReservationRequest.SeatSelection(1, flightId, "3B"))));

        assertThat(booked.reservations()).extracting(ReservationResponse::getSeatNumber).containsExactly("3A", "3B");
        assertThat(booked.reservations()).extracting(ReservationResponse::getBookingGroupId)
                .containsOnly(booked.bookingGroupId());
        ReservationResponse ada = booked.reservations().get(0);
        assertThat(ada.getFarePrice()).isEqualByComparingTo("100.00");
        assertThat(ada.getTotalPrice()).isEqualByComparingTo("106.00");
        assertThat(ada.getAncillaries()).singleElement().satisfies(line -> {
            assertThat(line.productCode()).isEqualTo("SEAT_PREFERRED");
            assertThat(line.price()).isEqualByComparingTo("6.00");
        });
        assertThat(booked.totalPrice()).isEqualByComparingTo("206.00");

        // Booked seats are TAKEN for everyone, and the holds are gone.
        actAs(bob);
        SeatMapResponse map = seatMaps.getSeatMap(flightId, null, CabinClass.ECONOMY, FareType.STANDARD, 0);
        assertThat(seatOf(map, "3A").status()).isEqualTo(SeatStatus.TAKEN);
        assertThatThrownBy(() -> seats.holdForBooking(hold("3A", 0, CabinClass.ECONOMY)))
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void bookingWithASeatWhoseHoldExpiredIsA409AndBooksNothing() {
        actAs(alice);
        assertThatThrownBy(() -> reservations.bookGroup(new GroupReservationRequest(
                List.of(passenger("Ada")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.ECONOMY, FareType.STANDARD)),
                List.of(new GroupReservationRequest.SeatSelection(0, flightId, "3C")))))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("hold on seat 3C has expired")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changingASeatAfterBookingReplacesTheFeeLine() {
        actAs(alice);
        ReservationResponse booked = reservations.bookGroup(new GroupReservationRequest(
                List.of(passenger("Ada")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.ECONOMY, FareType.BASIC)),
                List.of())).reservations().get(0);
        assertThat(booked.getSeatNumber()).isNull();

        seats.holdForReservation(booked.getId(), "4A"); // exit row on BASIC: 32.00
        seats.confirmSeat(booked.getId(), "4A");
        seats.holdForReservation(booked.getId(), "5B"); // middle on BASIC: 8.00
        seats.confirmSeat(booked.getId(), "5B");

        ReservationResponse after = reservations.getReservation(booked.getId());
        assertThat(after.getSeatNumber()).isEqualTo("5B");
        assertThat(after.getTotalPrice()).isEqualByComparingTo(after.getFarePrice().add(new java.math.BigDecimal("8.00")));
        assertThat(after.getAncillaries()).extracting(l -> l.seatNumber() + ":" + l.status())
                .containsExactly("4A:CANCELLED", "5B:ACTIVE");
    }

    @Test
    void aCabinTheAircraftDoesNotHaveCannotBeSoldAndAFullCabinIsSoldOut() {
        actAs(alice);
        assertThatThrownBy(() -> reservations.bookGroup(new GroupReservationRequest(List.of(passenger("Ada")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.FIRST, FareType.STANDARD)), List.of())))
                .hasMessageContaining("no First cabin");

        reservations.bookGroup(new GroupReservationRequest(
                List.of(passenger("A"), passenger("B"), passenger("C"), passenger("D")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.BUSINESS, FareType.STANDARD)), List.of()));
        assertThatThrownBy(() -> reservations.bookGroup(new GroupReservationRequest(List.of(passenger("E")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.BUSINESS, FareType.STANDARD)), List.of())))
                .hasMessageContaining("Business is sold out");
    }

    private SeatHoldRequest hold(String seat, int passengerIndex, CabinClass cabin) {
        return new SeatHoldRequest(flightId, seat, passengerIndex, cabin, FareType.STANDARD);
    }

    private static SeatMapResponse.SeatView seatOf(SeatMapResponse map, String code) {
        return map.seats().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
    }
}
