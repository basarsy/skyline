package com.basarsy.skyline.schedule.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI scheduleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Skyline schedule API")
                .description("Flights, status changes and seat inventory. All date-times are UTC "
                        + "(ISO-8601 local date-time, no offset); airports carry their IANA time zone "
                        + "for display."));
    }
}
