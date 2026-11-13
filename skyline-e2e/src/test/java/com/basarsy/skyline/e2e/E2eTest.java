package com.basarsy.skyline.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base for end-to-end tests. Everything goes through the API gateway against a running stack
 * ({@code docker compose up}), exactly as a client would. Each test creates its own aircraft, flight and
 * users, so tests don't depend on each other or on leftover data from earlier runs.
 */
abstract class E2eTest {

    static final String BASE_URL = property("skyline.e2e.baseUrl", "http://localhost:8000/api/v1");
    static final String ADMIN_EMAIL = property("skyline.e2e.adminEmail", "admin@skyline.local");
    static final String ADMIN_PASSWORD = property("skyline.e2e.adminPassword", null);
    static final String AUTH_DB_URL = property("skyline.e2e.authDbUrl", "jdbc:postgresql://localhost:5433/skyline_auth");

    /** Seeded by the route service's V2 migration. */
    static final String IST = "11111111-1111-1111-1111-111111111101";
    static final String LHR = "11111111-1111-1111-1111-111111111102";
    /** Seeded by the fleet service's V2 migration: Airbus A320neo, 180 seats. */
    static final String A320NEO = "22222222-2222-2222-2222-222222222201";

    static final String PASSWORD = "Passw0rd!!";

    private static volatile boolean stackReady;
    /** Remembered so later test classes fail fast instead of each waiting out the full timeout again. */
    private static volatile RuntimeException stackFailure;

    static String adminToken;

    @BeforeAll
    static void waitForStack() {
        if (ADMIN_PASSWORD == null || ADMIN_PASSWORD.isBlank()) {
            throw new IllegalStateException(
                    "Set SKYLINE_ADMIN_EMAIL / SKYLINE_ADMIN_PASSWORD (the same values the auth service was started with)");
        }
        if (stackFailure != null) {
            throw stackFailure;
        }
        if (!stackReady) {
            try {
                awaitAllServicesReachable();
            } catch (RuntimeException e) {
                stackFailure = e;
                throw e;
            }
            stackReady = true;
        }
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).then().statusCode(200).extract().path("data.accessToken");
    }

    /**
     * Services start in any order and take a while (Flyway, Kafka). Probe one endpoint per service through
     * the gateway until none answers with a 5xx (gateway: backend unreachable).
     */
    private static void awaitAllServicesReachable() {
        Awaitility.await("all Skyline services reachable through the gateway")
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(3))
                // Only "not up yet" is worth retrying. Anything else (e.g. a broken test setup) must fail now,
                // not after the timeout.
                .ignoreExceptionsInstanceOf(java.io.IOException.class)
                .until(() -> {
                    Response login = login(ADMIN_EMAIL, ADMIN_PASSWORD);
                    if (login.statusCode() != 200) {
                        return false;
                    }
                    String token = login.path("data.accessToken");
                    String unknown = UUID.randomUUID().toString();
                    return List.of(
                                    api(null).get("/airports"),                                   // route
                                    api(token).get("/aircraft-types"),                            // fleet
                                    api(null).get("/flights/" + unknown),                         // schedule
                                    api(token).get("/reservations/" + unknown),                   // reservation
                                    api(token).get("/checkin/ZZZZZZ/boarding-pass"),              // check-in
                                    api(token).get("/flights/" + unknown + "/crew"))              // crew
                            .stream()
                            .allMatch(r -> r.statusCode() < 500);
                });
    }

    // ---------------------------------------------------------------- HTTP helpers

    static RequestSpecification api(String token) {
        RequestSpecification spec = given().baseUri(BASE_URL).contentType(ContentType.JSON).accept(ContentType.JSON);
        return token == null ? spec : spec.auth().oauth2(token);
    }

    static Response login(String email, String password) {
        return api(null).body(Map.of("email", email, "password", password)).post("/auth/login");
    }

    // ---------------------------------------------------------------- scenario helpers

    record User(String id, String email, String accessToken, String refreshToken) {}

    static User registerPassenger(String name) {
        // Lowercase: auth stores emails lowercased, and tests look users up by email in the database.
        String email = (name + "-" + shortId() + "@e2e.test").toLowerCase();
        String id = api(null).body(Map.of("email", email, "password", PASSWORD)).post("/auth/register")
                .then().statusCode(201).extract().path("data.id");
        Response login = login(email, PASSWORD).then().statusCode(200).extract().response();
        return new User(id, email, login.path("data.accessToken"), login.path("data.refreshToken"));
    }

    /** A fresh flight IST→LHR departing in 24h (inside the check-in window) on a new 180-seat aircraft. */
    static String createFlight() {
        String aircraftId = api(adminToken)
                .body(Map.of("tailNumber", "TC-" + shortId(), "aircraftTypeId", A320NEO, "manufacturedYear", 2020))
                .post("/aircraft").then().statusCode(201).extract().path("data.id");

        LocalDateTime departure = LocalDateTime.now().plusHours(24).truncatedTo(ChronoUnit.MINUTES);
        return api(adminToken)
                .body(Map.of(
                        "flightNumber", "E2E" + shortId().substring(0, 4),
                        "routeId", istToLhrRoute(),
                        "aircraftId", aircraftId,
                        "departureTime", departure.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "arrivalTime", departure.plusHours(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "basePrice", 100.00))
                .post("/flights").then().statusCode(201).extract().path("data.id");
    }

    /** The IST→LHR route, created on first use (it survives between runs, so look it up first). */
    static String istToLhrRoute() {
        List<String> existing = api(null).queryParam("originIata", "IST").queryParam("destinationIata", "LHR")
                .get("/routes/search").then().statusCode(200).extract().path("data.id");
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return api(adminToken)
                .body(Map.of("originAirportId", IST, "destinationAirportId", LHR,
                        "distanceKm", 2500, "estimatedDurationMinutes", 240))
                .post("/routes").then().statusCode(201).extract().path("data.id");
    }

    record Booking(String id, String pnr) {}

    static Booking book(User user, String flightId, String firstName, String lastName, String passport) {
        Response response = api(user.accessToken())
                .body(Map.of(
                        "flightId", flightId,
                        "cabinClass", "ECONOMY",
                        "passenger", Map.of(
                                "firstName", firstName, "lastName", lastName, "passportNumber", passport,
                                "dateOfBirth", "1990-01-01", "nationality", "TR")))
                .post("/reservations");
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(201);
        return new Booking(response.path("data.id"), response.path("data.pnr"));
    }

    static int availableSeats(String flightId) {
        return api(null).get("/flights/" + flightId).then().statusCode(200).extract().path("data.availableSeats");
    }

    static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static String property(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() || value.startsWith("${") ? fallback : value;
    }
}
