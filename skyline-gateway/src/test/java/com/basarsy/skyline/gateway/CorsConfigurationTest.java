package com.basarsy.skyline.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * CORS preflights are answered by the gateway itself, so no downstream service needs to be running. Uses a
 * plain HTTP client against the real port so the requests look exactly like a browser's.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "SKYLINE_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://app.skyline.test")
class CorsConfigurationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void preflightFromAnAllowedOriginIsAccepted() throws Exception {
        HttpResponse<Void> response = preflight("/api/v1/flights", "http://localhost:5173", "GET");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly("http://localhost:5173");
        assertThat(response.headers().firstValue("Access-Control-Max-Age")).hasValue("3600");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }

    @Test
    void everyOriginInTheListIsAllowedWithTheConfiguredHeaders() throws Exception {
        HttpResponse<Void> response = preflight("/api/v1/reservations", "https://app.skyline.test", "PATCH");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("https://app.skyline.test");
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow())
                .containsIgnoringCase("authorization").containsIgnoringCase("content-type");
    }

    @Test
    void preflightFromAnUnlistedOriginGetsNoAllowHeader() throws Exception {
        HttpResponse<Void> response = preflight("/api/v1/flights", "https://evil.example", "GET");

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void headersOutsideTheListAreRefused() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/flights"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-Skyline-Internal-Key")
                .build();

        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpResponse<Void> preflight(String path, String origin, String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "authorization, content-type")
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
