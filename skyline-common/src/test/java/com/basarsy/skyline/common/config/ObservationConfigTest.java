package com.basarsy.skyline.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ObservationConfigTest {

    private final ObservationPredicate predicate = new ObservationConfig().skipActuatorAndScheduledRuns();

    @Test
    void probesScrapesAndPollersAreNotTracedRealRequestsAre() {
        assertThat(predicate.test("http.server.requests", request("/actuator/health/readiness"))).isFalse();
        assertThat(predicate.test("http.server.requests", request("/actuator/prometheus"))).isFalse();
        assertThat(predicate.test("tasks.scheduled.execution", new Observation.Context())).isFalse();

        assertThat(predicate.test("http.server.requests", request("/api/v1/reservations"))).isTrue();
        assertThat(predicate.test("http.client.requests", new Observation.Context())).isTrue();
    }

    private static ServerRequestObservationContext request(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri),
                new MockHttpServletResponse());
    }
}
