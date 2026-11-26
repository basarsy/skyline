package com.basarsy.skyline.common.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * What is not observed (no trace, no span): requests to {@code /actuator} (health probes every few seconds,
 * Prometheus scrapes) and @Scheduled runs (the outbox and email pollers, every second or so, mostly with nothing
 * to do). They would bury the traces of real requests. An event the outbox publishes still continues the trace
 * of the request that recorded it (see OutboxTracing).
 */
@Configuration
public class ObservationConfig {

    @Bean
    public ObservationPredicate skipActuatorAndScheduledRuns() {
        return (name, context) -> {
            if (name.startsWith("tasks.scheduled")) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext request) {
                return !request.getCarrier().getRequestURI().startsWith("/actuator");
            }
            return true;
        };
    }
}
