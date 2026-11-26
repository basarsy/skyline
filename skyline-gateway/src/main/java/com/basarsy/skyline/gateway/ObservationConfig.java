package com.basarsy.skyline.gateway;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/** Health probes of the gateway itself are not traced: they would bury the traces of real requests. */
@Configuration
public class ObservationConfig {

    @Bean
    public ObservationPredicate skipActuator() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request)
                || !request.getCarrier().getPath().value().startsWith("/actuator");
    }
}
