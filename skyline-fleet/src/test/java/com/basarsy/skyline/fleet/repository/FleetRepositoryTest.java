package com.basarsy.skyline.fleet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.entity.AircraftType;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real queries, constraints and Flyway migrations (incl. seed data) against PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class FleetRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID A320NEO = UUID.fromString("22222222-2222-2222-2222-222222222201");

    @Autowired
    private AircraftRepository aircraft;

    @Autowired
    private AircraftTypeRepository types;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void clean() {
        aircraft.deleteAll();
    }

    @Test
    void migrationsSeedTheThreeAircraftTypes() {
        assertThat(types.findAll()).extracting(AircraftType::getModel)
                .containsExactlyInAnyOrder("A320neo", "737-800", "787-9");
        assertThat(types.findById(A320NEO)).get().satisfies(type -> {
            assertThat(type.getManufacturer()).isEqualTo("Airbus");
            assertThat(type.getTotalSeats()).isEqualTo(180);
            assertThat(type.getCabinConfig()).isEqualTo("Y180");
            assertThat(type.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void typeExistenceIgnoresCase() {
        assertThat(types.existsByManufacturerIgnoreCaseAndModelIgnoreCase("airbus", "A320NEO")).isTrue();
        assertThat(types.existsByManufacturerIgnoreCaseAndModelIgnoreCase("Airbus", "A321neo")).isFalse();
    }

    @Test
    void savingAnAircraftFillsAuditColumns() {
        Aircraft saved = save("TC-ONE");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void tailNumberExistenceIgnoresCase() {
        save("TC-ONE");

        assertThat(aircraft.existsByTailNumberIgnoreCase("tc-one")).isTrue();
        assertThat(aircraft.existsByTailNumberIgnoreCase("TC-TWO")).isFalse();
    }

    @Test
    void tailNumbersAreUnique() {
        save("TC-ONE");

        assertThatThrownBy(() -> save("TC-ONE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAircraftNeedsAnExistingType() {
        AircraftType ghost = new AircraftType();
        ghost.setId(UUID.randomUUID());

        assertThatThrownBy(() -> save("TC-GHOST", ghost)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fetchJoinQueriesReturnInitializedTypes() {
        Aircraft saved = save("TC-ONE");
        em.clear();

        assertThat(aircraft.findAllWithAircraftType()).singleElement().satisfies(found -> {
            assertThat(Hibernate.isInitialized(found.getAircraftType())).isTrue();
            assertThat(found.getAircraftType().getModel()).isEqualTo("A320neo");
        });
        em.clear();

        assertThat(aircraft.findByIdWithAircraftType(saved.getId())).get().satisfies(found -> {
            assertThat(Hibernate.isInitialized(found.getAircraftType())).isTrue();
            assertThat(found.getAircraftType().getTotalSeats()).isEqualTo(180);
        });
        assertThat(aircraft.findByIdWithAircraftType(UUID.randomUUID())).isEmpty();
    }

    private Aircraft save(String tailNumber) {
        return save(tailNumber, types.getReferenceById(A320NEO));
    }

    private Aircraft save(String tailNumber, AircraftType type) {
        Aircraft a = new Aircraft();
        a.setTailNumber(tailNumber);
        a.setAircraftType(type);
        a.setStatus(AircraftStatus.ACTIVE);
        a.setManufacturedYear(2020);
        return aircraft.saveAndFlush(a);
    }
}
