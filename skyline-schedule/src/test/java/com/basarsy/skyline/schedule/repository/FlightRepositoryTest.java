package com.basarsy.skyline.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.service.DepartureDay;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real queries against PostgreSQL with the Flyway schema. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class FlightRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID ROUTE = UUID.randomUUID();
    private static final LocalDateTime DAY = LocalDateTime.of(2030, 6, 1, 0, 0);

    @Autowired
    private FlightRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void searchReturnsEveryRequestedStatusAndNothingElse() {
        Flight scheduled = save("SK1", ROUTE, DAY.plusHours(8), FlightStatus.SCHEDULED);
        Flight delayed = save("SK2", ROUTE, DAY.plusHours(9), FlightStatus.DELAYED);
        save("SK3", ROUTE, DAY.plusHours(10), FlightStatus.CANCELLED);
        save("SK4", ROUTE, DAY.plusHours(11), FlightStatus.BOARDING);
        save("SK5", ROUTE, DAY.plusDays(1).plusHours(1), FlightStatus.SCHEDULED); // next day
        save("SK6", UUID.randomUUID(), DAY.plusHours(8), FlightStatus.SCHEDULED); // other route

        List<Flight> found = repository.searchFlights(List.of(ROUTE), DAY, DAY.plusDays(1),
                EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED), PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(Flight::getId).containsExactlyInAnyOrder(scheduled.getId(), delayed.getId());
    }

    @Test
    void searchingAnIstanbulDateFindsItsNightDeparturesStoredOnThePreviousUtcDay() {
        // 1 June in Istanbul (UTC+3) runs from 31 May 21:00 to 1 June 21:00 UTC.
        save("SK0", ROUTE, DAY.minusHours(3).minusMinutes(1), FlightStatus.SCHEDULED); // 23:59 on 31 May locally
        Flight night = save("SK1", ROUTE, DAY.minusMinutes(90), FlightStatus.SCHEDULED); // 01:30 on 1 June locally
        Flight evening = save("SK2", ROUTE, DAY.plusHours(20).plusMinutes(59), FlightStatus.SCHEDULED); // 23:59
        save("SK3", ROUTE, DAY.plusHours(21), FlightStatus.SCHEDULED);                  // 00:00 on 2 June locally

        DepartureDay june1 = DepartureDay.of(DAY.toLocalDate(), "Europe/Istanbul");
        List<Flight> found = repository.searchFlights(List.of(ROUTE), june1.start(), june1.end(), BOOKABLE,
                PageRequest.of(0, 20, Sort.by("departureTime"))).getContent();

        assertThat(found).extracting(Flight::getId).containsExactly(night.getId(), evening.getId());
    }

    @Test
    void boardWithoutFiltersReturnsEveryStatusSortedByDeparture() {
        Flight cancelled = save("SK3", ROUTE, DAY.plusHours(10), FlightStatus.CANCELLED);
        Flight arrived = save("SK1", ROUTE, DAY.plusHours(2), FlightStatus.ARRIVED);
        Flight boarding = save("SK2", UUID.randomUUID(), DAY.plusHours(5), FlightStatus.BOARDING);

        List<Flight> board = board(new FlightBoardFilter(null, null, null, null));

        assertThat(board).extracting(Flight::getId)
                .containsExactly(arrived.getId(), boarding.getId(), cancelled.getId());
    }

    @Test
    void boardDateIsAWholeUtcDay() {
        save("SK0", ROUTE, DAY.minusNanos(1_000), FlightStatus.SCHEDULED);     // 23:59:59.999999 the day before
        Flight first = save("SK1", ROUTE, DAY, FlightStatus.SCHEDULED);          // 00:00 sharp
        Flight last = save("SK2", ROUTE, DAY.plusDays(1).minusSeconds(1), FlightStatus.DEPARTED);
        save("SK3", ROUTE, DAY.plusDays(1), FlightStatus.SCHEDULED);              // next day 00:00

        assertThat(board(new FlightBoardFilter(DAY.toLocalDate(), null, null, null)))
                .extracting(Flight::getId).containsExactly(first.getId(), last.getId());
    }

    @Test
    void boardFiltersCombine() {
        UUID otherRoute = UUID.randomUUID();
        Flight match = save("SKY101", ROUTE, DAY.plusHours(8), FlightStatus.DELAYED);
        save("SKY102", ROUTE, DAY.plusHours(9), FlightStatus.SCHEDULED);               // wrong status
        save("SKY103", otherRoute, DAY.plusHours(9), FlightStatus.DELAYED);            // wrong route
        save("XY101", ROUTE, DAY.plusHours(9), FlightStatus.BOARDING);                 // wrong number
        save("SKY104", ROUTE, DAY.plusDays(2), FlightStatus.DELAYED);                  // wrong day
        Flight boarding = save("sky105", ROUTE, DAY.plusHours(11), FlightStatus.BOARDING);

        var filter = new FlightBoardFilter(DAY.toLocalDate(),
                EnumSet.of(FlightStatus.DELAYED, FlightStatus.BOARDING), "sky1", ROUTE);

        assertThat(board(filter)).extracting(Flight::getId).containsExactly(match.getId(), boarding.getId());
    }

    @Test
    void boardFlightNumberSearchTreatsLikeWildcardsLiterally() {
        save("SK100", ROUTE, DAY, FlightStatus.SCHEDULED);
        Flight literal = save("SK_1", ROUTE, DAY.plusHours(1), FlightStatus.SCHEDULED);

        assertThat(board(new FlightBoardFilter(null, null, "_", null))).extracting(Flight::getId)
                .containsExactly(literal.getId());
        assertThat(board(new FlightBoardFilter(null, null, "%", null))).isEmpty();
    }

    @Test
    void seatUpdatesBumpTheVersionSoAStaleSaveConflicts() {
        Flight flight = save("SK1", ROUTE, DAY, FlightStatus.SCHEDULED);
        long version = flight.getVersion();

        assertThat(repository.decrementSeat(flight.getId(), BOOKABLE, DAY)).isEqualTo(1);
        Flight reloaded = repository.findById(flight.getId()).orElseThrow();
        assertThat(reloaded.getAvailableSeats()).isEqualTo(179);
        assertThat(reloaded.getVersion()).isEqualTo(version + 1);

        assertThat(repository.incrementSeat(flight.getId(), FlightStatus.CANCELLED, DAY)).isEqualTo(1);
        assertThat(repository.findById(flight.getId()).orElseThrow().getVersion()).isEqualTo(version + 2);

        flight.setFlightNumber("SK9"); // still carries the version from before both seat updates
        assertThatThrownBy(() -> repository.saveAndFlush(flight))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void seatsAreOnlyHeldOnBookableFlightsAndNeverReturnedToCancelledOnes() {
        Flight cancelled = save("SK1", ROUTE, DAY, FlightStatus.CANCELLED);
        Flight boarding = save("SK2", ROUTE, DAY, FlightStatus.BOARDING);
        Flight delayed = save("SK3", ROUTE, DAY, FlightStatus.DELAYED);

        assertThat(repository.decrementSeat(cancelled.getId(), BOOKABLE, DAY)).isZero();
        assertThat(repository.decrementSeat(boarding.getId(), BOOKABLE, DAY)).isZero();
        assertThat(repository.decrementSeat(delayed.getId(), BOOKABLE, DAY)).isEqualTo(1);
        assertThat(repository.incrementSeat(cancelled.getId(), FlightStatus.CANCELLED, DAY)).isZero();
        assertThat(repository.findById(cancelled.getId()).orElseThrow().getAvailableSeats()).isEqualTo(180);
    }

    private static final Set<FlightStatus> BOOKABLE = EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    private List<Flight> board(FlightBoardFilter filter) {
        return repository.findAll(FlightSpecifications.matching(filter),
                PageRequest.of(0, 50, Sort.by("departureTime"))).getContent();
    }

    Flight save(String number, UUID routeId, LocalDateTime departure, FlightStatus status) {
        Flight flight = new Flight();
        flight.setFlightNumber(number);
        flight.setRouteId(routeId);
        flight.setAircraftId(UUID.randomUUID());
        flight.setDepartureTime(departure);
        flight.setArrivalTime(departure.plusHours(4));
        flight.setStatus(status);
        flight.setAvailableSeats(180);
        flight.setBasePrice(new BigDecimal("100.00"));
        return repository.saveAndFlush(flight);
    }
}
