package com.basarsy.skyline.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/** The gateway answers CORS preflights for the configured browser origins (default: the web dev server). */
class CorsIT extends E2eTest {

    @Test
    void preflightFromTheWebOriginIsAllowed() {
        Response response = preflight("http://localhost:5173");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }

    @Test
    void preflightFromAnUnlistedOriginGetsNoAllowHeader() {
        assertThat(preflight("https://evil.example").header("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void actualResponsesCarryASingleAllowOriginHeader() {
        Response response = given().baseUri(BASE_URL).header("Origin", "http://localhost:5173").get("/airports");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().getValues("Access-Control-Allow-Origin")).containsExactly("http://localhost:5173");
    }

    private static Response preflight(String origin) {
        return given().baseUri(BASE_URL)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .options("/flights");
    }
}
