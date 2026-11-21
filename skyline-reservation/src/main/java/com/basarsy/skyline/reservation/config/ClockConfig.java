package com.basarsy.skyline.reservation.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Every LocalDateTime in Skyline is UTC; seat-change cut-offs and caches read this clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
