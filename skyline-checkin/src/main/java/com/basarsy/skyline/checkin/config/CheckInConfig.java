package com.basarsy.skyline.checkin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CheckInConfig {

    /** Every LocalDateTime in Skyline is UTC, so the check-in window is evaluated against a UTC clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI checkInOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Skyline check-in API")
                .description("Online check-in and boarding passes. All date-times are UTC "
                        + "(ISO-8601 local date-time, no offset)."));
    }
}
