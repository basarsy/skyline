package com.basarsy.skyline.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
