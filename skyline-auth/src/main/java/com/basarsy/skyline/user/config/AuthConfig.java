package com.basarsy.skyline.user.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Auth-service-only wiring: kept out of skyline-common so scheduling is enabled in this service alone. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class AuthConfig {

    /** UTC, like every LocalDateTime in Skyline. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
