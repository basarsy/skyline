package com.basarsy.skyline.user.bootstrap;

import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the initial ADMIN account on startup when {@code skyline.bootstrap.admin.email} and
 * {@code skyline.bootstrap.admin.password} are set. Public registration only ever creates
 * PASSENGER accounts, so without this there is no way to obtain STAFF/ADMIN privileges.
 *
 * <p>Idempotent: if a user with that email already exists it is left untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${skyline.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${skyline.bootstrap.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.info("Admin bootstrap skipped: skyline.bootstrap.admin.email/password not set");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "skyline.bootstrap.admin.password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap skipped: user {} already exists", email);
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN account {}", email);
    }
}
