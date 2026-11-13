package com.basarsy.skyline.crew.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.CrewRole;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real queries and migrations against PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class CrewMemberRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("lastName").ignoreCase(),
            Sort.Order.asc("firstName").ignoreCase(), Sort.Order.asc("employeeNumber"));

    @Autowired
    private CrewMemberRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void listsEveryoneSortedByLastNameIgnoringCase() {
        save("E1", "Zoe", "adams", CrewRole.PURSER);
        save("E2", "Ali", "Yilmaz", CrewRole.CAPTAIN);
        save("E3", "Bea", "Brown", CrewRole.FIRST_OFFICER);

        assertThat(find(null, null)).extracting(CrewMember::getLastName).containsExactly("adams", "Brown", "Yilmaz");
    }

    @Test
    void filtersByRoleAndByNameOrEmployeeNumberAndCombines() {
        save("E100", "Ada", "Lovelace", CrewRole.CAPTAIN);
        save("E200", "Grace", "Hopper", CrewRole.FIRST_OFFICER);
        save("X300", "Alan", "Turing", CrewRole.CAPTAIN);

        assertThat(find(CrewRole.CAPTAIN, null)).extracting(CrewMember::getEmployeeNumber)
                .containsExactly("E100", "X300");
        assertThat(find(null, "HOP")).extracting(CrewMember::getEmployeeNumber).containsExactly("E200");
        assertThat(find(null, "ada")).extracting(CrewMember::getEmployeeNumber).containsExactly("E100");
        assertThat(find(null, "e2")).extracting(CrewMember::getEmployeeNumber).containsExactly("E200");
        assertThat(find(CrewRole.CAPTAIN, "a")).extracting(CrewMember::getEmployeeNumber)
                .containsExactly("E100", "X300");
        assertThat(find(CrewRole.FIRST_OFFICER, "turing")).isEmpty();
        assertThat(find(null, "%")).isEmpty();
    }

    @Test
    void employeeNumbersAreUniqueIgnoringCase() {
        save("E500", "Ada", "Lovelace", CrewRole.CAPTAIN);

        assertThat(repository.existsByEmployeeNumberIgnoreCase("e500")).isTrue();
        assertThatThrownBy(() -> save("e500", "Other", "Person", CrewRole.PURSER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<CrewMember> find(CrewRole role, String q) {
        return repository.findAll(CrewMemberSpecifications.matching(role, q), BY_NAME);
    }

    private CrewMember save(String employeeNumber, String first, String last, CrewRole role) {
        CrewMember member = new CrewMember();
        member.setUserId(UUID.randomUUID());
        member.setEmployeeNumber(employeeNumber);
        member.setFirstName(first);
        member.setLastName(last);
        member.setRole(role);
        member.setLicenseNumber("L-" + employeeNumber);
        member.setLicenseExpiryDate(LocalDate.of(2035, 1, 1));
        return repository.saveAndFlush(member);
    }
}
