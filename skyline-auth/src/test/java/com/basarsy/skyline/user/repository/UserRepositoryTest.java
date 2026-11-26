package com.basarsy.skyline.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.common.config.JpaAuditingConfig;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The login-lockout statements against PostgreSQL with the Flyway schema. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 6, 1, 9, 0);

    @Autowired
    private UserRepository users;

    @Test
    void theLastAllowedFailureLocksTheAccountAndStartsTheCountAgain() {
        UUID id = user().getId();

        for (int i = 1; i < 3; i++) {
            users.recordFailedLogin(id, 3, NOW.plusMinutes(15));
            assertThat(reload(id).getFailedLogins()).isEqualTo(i);
            assertThat(reload(id).getLockedUntil()).isNull();
        }
        users.recordFailedLogin(id, 3, NOW.plusMinutes(15));

        User locked = reload(id);
        assertThat(locked.getLockedUntil()).isEqualTo(NOW.plusMinutes(15));
        assertThat(locked.getFailedLogins()).isZero();
    }

    @Test
    void aSuccessfulLoginClearsTheCountAndTheLock() {
        UUID id = user().getId();
        users.recordFailedLogin(id, 1, NOW.plusMinutes(15));

        users.clearFailedLogins(id);

        assertThat(reload(id).getLockedUntil()).isNull();
        assertThat(reload(id).getFailedLogins()).isZero();
    }

    private User user() {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@test.local");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PASSENGER);
        return users.saveAndFlush(user);
    }

    private User reload(UUID id) {
        return users.findById(id).orElseThrow();
    }
}
