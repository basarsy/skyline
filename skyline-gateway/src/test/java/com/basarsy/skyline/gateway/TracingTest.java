package com.basarsy.skyline.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A request's trace starts at the gateway: the service behind it gets the trace as a W3C traceparent, and the
 * response names the same trace in X-Trace-Id. The service is a stub HTTP server; no Docker, no Redis (the route
 * used isn't rate limited).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.sampling.probability=1.0")
@AutoConfigureObservability // tests turn tracing off unless asked
class TracingTest {

    private static final AtomicReference<String> RECEIVED_TRACEPARENT = new AtomicReference<>();
    private static final HttpServer SCHEDULE = stubService();

    @DynamicPropertySource
    static void routeFlightsToTheStub(DynamicPropertyRegistry registry) {
        registry.add("SCHEDULE_SERVICE_URL", () -> "http://127.0.0.1:" + SCHEDULE.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        SCHEDULE.stop(0);
    }

    @LocalServerPort
    private int port;

    @Test
    void theServiceBehindGetsTheTraceTheResponseNames() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/flights/board")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        String traceId = response.headers().firstValue(TraceIdResponseHeader.HEADER).orElseThrow();
        assertThat(traceId).matches("[0-9a-f]{32}");
        // traceparent: version-traceid-spanid-flags
        assertThat(RECEIVED_TRACEPARENT.get()).isNotNull().startsWith("00-" + traceId + "-");
    }

    @Test
    void theGatewaysOwnMetricsAreNotPublic() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus")).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isNotEqualTo(200);
    }

    private static HttpServer stubService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECEIVED_TRACEPARENT.set(exchange.getRequestHeaders().getFirst("traceparent"));
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
