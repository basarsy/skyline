package com.basarsy.skyline.route.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.route.entity.Airport;
import com.basarsy.skyline.route.entity.Route;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real queries, constraints and Flyway migrations (incl. seed airports) against PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class RouteRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID IST = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID LHR = UUID.fromString("11111111-1111-1111-1111-111111111102");
    private static final UUID JFK = UUID.fromString("11111111-1111-1111-1111-111111111103");

    @Autowired
    private AirportRepository airports;

    @Autowired
    private RouteRepository routes;

    @BeforeEach
    void clean() {
        routes.deleteAll();
    }

    @Test
    void migrationsSeedTheThreeAirports() {
        assertThat(airports.findAll()).extracting(Airport::getIataCode).containsExactlyInAnyOrder("IST", "LHR", "JFK");
        assertThat(airports.findById(JFK)).get().satisfies(jfk -> {
            assertThat(jfk.getIcaoCode()).isEqualTo("KJFK");
            assertThat(jfk.getTimezone()).isEqualTo("America/New_York");
        });
    }

    @Test
    void findsAndChecksAirportsByIataCode() {
        assertThat(airports.findByIataCode("LHR")).get().extracting(Airport::getId).isEqualTo(LHR);
        assertThat(airports.existsByIataCode("IST")).isTrue();
        assertThat(airports.existsByIataCode("SAW")).isFalse();
    }

    @Test
    void iataCodesAreUnique() {
        Airport copy = new Airport();
        copy.setIataCode("IST");
        copy.setIcaoCode("LTBA");
        copy.setName("Ataturk");
        copy.setCity("Istanbul");
        copy.setCountry("Turkey");
        copy.setTimezone("Europe/Istanbul");

        assertThatThrownBy(() -> airports.saveAndFlush(copy)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchMatchesOriginAndDestinationInThatDirection() {
        Route istLhr = save(IST, LHR);
        save(LHR, IST);
        save(IST, JFK);

        assertThat(routes.findByOrigin_IataCodeAndDestination_IataCode("IST", "LHR"))
                .extracting(Route::getId).containsExactly(istLhr.getId());
        assertThat(routes.findByOrigin_IataCodeAndDestination_IataCode("JFK", "LHR")).isEmpty();
    }

    @Test
    void aRouteBetweenTheSameAirportsInTheSameDirectionIsUnique() {
        save(IST, LHR);
        save(LHR, IST); // the reverse direction is a different route

        assertThatThrownBy(() -> save(IST, LHR)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Route save(UUID origin, UUID destination) {
        Route route = new Route();
        route.setOrigin(airports.getReferenceById(origin));
        route.setDestination(airports.getReferenceById(destination));
        route.setDistanceKm(2500);
        route.setEstimatedDurationMinutes(240);
        return routes.saveAndFlush(route);
    }
}
