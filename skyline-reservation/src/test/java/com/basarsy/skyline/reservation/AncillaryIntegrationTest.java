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
import com.basarsy.skyline.reservation.client.dto.AirportSummary;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.RouteResponse;
import com.basarsy.skyline.reservation.dto.AncillaryOffer;
import com.basarsy.skyline.reservation.dto.AncillaryOptionsResponse;
import com.basarsy.skyline.reservation.dto.AncillaryPurchaseRequest;
import com.basarsy.skyline.reservation.dto.GroupReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.ReservationAncillary;
import com.basarsy.skyline.reservation.entity.RouteScope;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import com.basarsy.skyline.reservation.service.AncillaryService;
import com.basarsy.skyline.reservation.service.ReservationService;
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

/** Bags and add-ons against the seeded catalog, prices and fare allowances (V5 migration). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AncillaryIntegrationTest {

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
    AncillaryService ancillaries;

    private final UUID international = UUID.randomUUID();
    private final UUID domestic = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(scheduleClient.getFlight(international)).thenReturn(SeatTestFixtures.flight(international)); // IST-LHR
        FlightResponse domesticFlight = SeatTestFixtures.flight(domestic);
        domesticFlight.setRoute(new RouteResponse(UUID.randomUUID(),
                new AirportSummary(UUID.randomUUID(), "IST", "Istanbul", "Istanbul", "Turkey", "Europe/Istanbul"),
                new AirportSummary(UUID.randomUUID(), "ESB", "Esenboga", "Ankara", "Turkey", "Europe/Istanbul"),
                350, 60));
        when(scheduleClient.getFlight(domestic)).thenReturn(domesticFlight);
        when(fleetClient.getSeatLayout(any())).thenReturn(SeatTestFixtures.layout());
        actAs(UUID.randomUUID());
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theOptionsShowWhatTheFareIncludesAndPriceByRoute() {
        ReservationResponse flex = book(international, FareType.FLEX);
        AncillaryOptionsResponse options = ancillaries.options(flex.getId(), null);

        assertThat(options.routeScope()).isEqualTo(RouteScope.INTERNATIONAL);
        assertThat(options.included()).extracting(AncillaryOptionsResponse.Included::code)
                .containsExactlyInAnyOrder("CABIN_BAG", "CHECKED_BAG_20", "PRIORITY_BOARDING");
        assertThat(offer(options, "CABIN_BAG").reason()).isEqualTo("Included in your fare");
        assertThat(offer(options, "CHECKED_BAG_20").price()).isEqualByComparingTo("40.00");

        ReservationResponse basic = book(domestic, FareType.BASIC);
        AncillaryOptionsResponse domesticOptions = ancillaries.options(basic.getId(), null);
        assertThat(domesticOptions.routeScope()).isEqualTo(RouteScope.DOMESTIC);
        assertThat(domesticOptions.included()).isEmpty();
        assertThat(offer(domesticOptions, "CHECKED_BAG_20").price()).isEqualByComparingTo("25.00");
        assertThat(offer(domesticOptions, "CABIN_BAG").available()).isTrue();
    }

    @Test
    void buyingBagsAddsServerPricedLinesToTheTotal() {
        ReservationResponse basic = book(international, FareType.BASIC);

        ancillaries.purchase(basic.getId(), new AncillaryPurchaseRequest(List.of(
                new AncillaryPurchaseRequest.Item("CHECKED_BAG_20", 2),
                new AncillaryPurchaseRequest.Item("EXTRA_WEIGHT_10", 1)), null));

        ReservationResponse after = reservations.getReservation(basic.getId());
        assertThat(after.getAncillaries()).extracting(l -> l.productCode() + " x" + l.quantity() + " " + l.price())
                .containsExactly("CHECKED_BAG_20 x2 80.00", "EXTRA_WEIGHT_10 x1 25.00");
        assertThat(after.getTotalPrice()).isEqualByComparingTo(after.getFarePrice().add(new java.math.BigDecimal("105.00")));
    }

    @Test
    void whatTheFareIncludesCannotBeBoughtAgainAndPassengersCannotSellAtAirportPrices() {
        ReservationResponse standard = book(international, FareType.STANDARD);

        assertThatThrownBy(() -> ancillaries.purchase(standard.getId(), new AncillaryPurchaseRequest(
                List.of(new AncillaryPurchaseRequest.Item("CABIN_BAG", 1)), null)))
                .isInstanceOf(SkylineException.class)
                .hasMessageContaining("Included in your fare")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThatThrownBy(() -> ancillaries.options(standard.getId(), SalesChannel.AIRPORT))
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void addOnsCanBeBoughtWithTheBookingAndAreRefundedOnCancellationOnlyOnFlex() {
        ReservationResponse basic = reservations.bookGroup(new GroupReservationRequest(List.of(passenger("Ada")),
                List.of(new GroupReservationRequest.Leg(international, CabinClass.ECONOMY, FareType.BASIC)), List.of(),
                List.of(new GroupReservationRequest.Extra(0, international, "CHECKED_BAG_20", 1),
                        new GroupReservationRequest.Extra(0, international, "MEAL_HOT", 1)))).reservations().get(0);
        assertThat(basic.getAncillaries()).hasSize(2);

        reservations.cancelReservation(basic.getId());

        // The bag on a BASIC fare is forfeited; the meal is always refundable.
        assertThat(reservations.getReservation(basic.getId()).getAncillaries())
                .extracting(l -> l.productCode() + ":" + l.status())
                .containsExactly("CHECKED_BAG_20:" + ReservationAncillary.Status.CANCELLED,
                        "MEAL_HOT:" + ReservationAncillary.Status.REFUNDED);
    }

    private ReservationResponse book(UUID flightId, FareType fare) {
        return reservations.bookGroup(new GroupReservationRequest(List.of(passenger("Ada")),
                List.of(new GroupReservationRequest.Leg(flightId, CabinClass.ECONOMY, fare)), List.of()))
                .reservations().get(0);
    }

    private static AncillaryOffer offer(AncillaryOptionsResponse options, String code) {
        return options.offers().stream().filter(o -> o.code().equals(code)).findFirst().orElseThrow();
    }
}
