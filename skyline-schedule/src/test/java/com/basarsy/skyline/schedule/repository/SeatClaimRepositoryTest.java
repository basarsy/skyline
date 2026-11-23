package com.basarsy.skyline.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The claim table's SQL against PostgreSQL with the Flyway schema. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, SeatClaimRepository.class})
@Testcontainers
class SeatClaimRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private FlightRepository flights;

    private UUID flightId;

    @BeforeEach
    void setUp() {
        flightId = flight("SK1").getId();
    }

    @Test
    void aClaimIsRecordedOnce() {
        UUID claim = UUID.randomUUID();

        assertThat(claims.insert(claim, flightId)).isTrue();
        assertThat(claims.insert(claim, flightId)).as("repeated").isFalse();
        assertThat(claims.find(claim)).contains(new SeatClaimRepository.Claim(flightId, false));
    }

    @Test
    void releasingGivesBackOnlyAHeldClaimOnThatFlightAndOnlyOnce() {
        UUID claim = UUID.randomUUID();
        UUID otherFlight = flight("SK2").getId();
        claims.insert(claim, flightId);

        assertThat(claims.release(claim, otherFlight)).as("other flight").isFalse();
        assertThat(claims.release(claim, flightId)).isTrue();
        assertThat(claims.release(claim, flightId)).as("already released").isFalse();
        assertThat(claims.find(claim)).contains(new SeatClaimRepository.Claim(flightId, true));
    }

    @Test
    void aReleaseThatOvertakesItsClaimLeavesAMarkerSoTheClaimIsRefused() {
        UUID claim = UUID.randomUUID();

        assertThat(claims.release(claim, flightId)).as("nothing held").isFalse();

        assertThat(claims.insert(claim, flightId)).as("arrives late").isFalse();
        assertThat(claims.find(claim)).contains(new SeatClaimRepository.Claim(flightId, true));
    }

    @Test
    void cancellingAFlightReleasesItsClaims() {
        UUID claim = UUID.randomUUID();
        claims.insert(claim, flightId);

        claims.releaseAllOnFlight(flightId);

        assertThat(claims.release(claim, flightId)).as("gives nothing back").isFalse();
    }

    private Flight flight(String number) {
        Flight flight = new Flight();
        flight.setFlightNumber(number);
        flight.setRouteId(UUID.randomUUID());
        flight.setAircraftId(UUID.randomUUID());
        flight.setDepartureTime(LocalDateTime.of(2030, 6, 1, 9, 0));
        flight.setArrivalTime(LocalDateTime.of(2030, 6, 1, 13, 0));
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setBasePrice(new BigDecimal("100.00"));
        flight.setAvailableSeats(180);
        return flights.saveAndFlush(flight);
    }
}
