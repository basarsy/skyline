package com.basarsy.skyline.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A call from one service to another (OpenFeign) continues the caller's trace: the callee gets a W3C traceparent
 * with the caller's trace id. This needs feign-micrometer on the classpath, which configuration alone doesn't show.
 */
@SpringBootTest(classes = FeignTracingTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "management.tracing.sampling.probability=1.0")
@AutoConfigureObservability // tests turn tracing off unless asked
class FeignTracingTest {

    private static final AtomicReference<String> RECEIVED_TRACEPARENT = new AtomicReference<>();
    private static final HttpServer CALLEE = stub();

    @DynamicPropertySource
    static void calleeUrl(DynamicPropertyRegistry registry) {
        registry.add("callee.url", () -> "http://127.0.0.1:" + CALLEE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        CALLEE.stop(0);
    }

    @Autowired
    private Callee callee;

    @Autowired
    private Tracer tracer;

    @Test
    void theCalleeGetsTheCallersTrace() {
        Span request = tracer.nextSpan().name("incoming request").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            callee.ping();
        } finally {
            request.end();
        }

        assertThat(RECEIVED_TRACEPARENT.get()).isNotNull()
                .startsWith("00-" + request.context().traceId() + "-");
    }

    @FeignClient(name = "callee", url = "${callee.url}")
    interface Callee {
        @GetMapping("/ping")
        String ping();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            KafkaAutoConfiguration.class, RedisAutoConfiguration.class, SecurityAutoConfiguration.class})
    @EnableFeignClients(clients = Callee.class)
    static class App {
    }

    private static HttpServer stub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECEIVED_TRACEPARENT.set(exchange.getRequestHeaders().getFirst("traceparent"));
                byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
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
