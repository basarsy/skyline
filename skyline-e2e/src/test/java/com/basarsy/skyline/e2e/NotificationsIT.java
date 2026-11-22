package com.basarsy.skyline.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * reservation / schedule -> Kafka -> notification: the in-app inbox through the gateway, and the emails as Mailpit
 * caught them.
 */
class NotificationsIT extends E2eTest {

    static final String MAILPIT_URL = System.getProperty("skyline.e2e.mailpitUrl",
            System.getenv().getOrDefault("SKYLINE_E2E_MAILPIT_URL", "http://localhost:8025"));

    @Test
    void aBookingAndADelayReachTheInboxAndTheContactEmail() {
        String flightId = createFlight();
        User ada = registerPassenger("ada");
        Booking booking = book(ada, flightId, "Ada", "Lovelace", "P1");

        awaitKinds(ada, "BOOKING_CONFIRMED");

        String departure = api(null).get("/flights/" + flightId).then().extract().path("data.departureTime");
        String estimate = java.time.LocalDateTime.parse(departure).plusMinutes(45)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        api(adminToken).body(Map.of("estimatedDepartureTime", estimate)).put("/flights/" + flightId + "/delay")
                .then().statusCode(200);
        api(adminToken).body(Map.of("gate", "B12")).put("/flights/" + flightId + "/gate").then().statusCode(200);

        awaitKinds(ada, "BOOKING_CONFIRMED", "FLIGHT_DELAYED", "FLIGHT_GATE");
        // The flight leaves in 24h, inside the check-in window: the reminder job (every minute) picks it up.
        awaitKinds(ada, Duration.ofSeconds(120), "CHECK_IN_OPEN");
        List<String> links = api(ada.accessToken()).get("/notifications").then().statusCode(200)
                .extract().path("data.content.link");
        assertThat(links).contains("/trips/" + booking.id());

        // The contact email defaulted to the account's; Mailpit holds what was sent to it.
        Awaitility.await("emails in Mailpit")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(subjectsSentTo(ada.email()))
                        .anyMatch(s -> s.startsWith("Booking confirmed"))
                        .anyMatch(s -> s.endsWith(" is delayed"))
                        .anyMatch(s -> s.contains(": gate B12"))
                        .anyMatch(s -> s.startsWith("Check-in is open")));

        int unread = api(ada.accessToken()).get("/notifications/unread-count").then().extract().path("data.count");
        assertThat(unread).isGreaterThanOrEqualTo(3);
        api(ada.accessToken()).post("/notifications/read-all").then().statusCode(200);
        assertThat((Integer) api(ada.accessToken()).get("/notifications/unread-count").then()
                .extract().path("data.count")).isZero();
    }

    @Test
    void someoneElseSeesNoneOfIt() {
        String flightId = createFlight();
        User ada = registerPassenger("ada");
        User eve = registerPassenger("eve");
        book(ada, flightId, "Ada", "Lovelace", "P1");

        awaitKinds(ada, "BOOKING_CONFIRMED");

        List<Object> evesInbox = api(eve.accessToken()).get("/notifications").then().statusCode(200)
                .extract().path("data.content");
        assertThat(evesInbox).isEmpty();
    }

    /** Waits until the user's inbox holds (at least) these kinds of notification. */
    private static void awaitKinds(User user, String... kinds) {
        awaitKinds(user, Duration.ofSeconds(60), kinds);
    }

    private static void awaitKinds(User user, Duration atMost, String... kinds) {
        Awaitility.await("notifications " + List.of(kinds))
                .atMost(atMost)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat((List<String>) api(user.accessToken()).get("/notifications")
                        .then().statusCode(200).extract().path("data.content.kind")).contains(kinds));
    }

    private static List<String> subjectsSentTo(String email) {
        return given().baseUri(MAILPIT_URL).queryParam("query", "to:\"" + email + "\"")
                .get("/api/v1/search").then().statusCode(200).extract().path("messages.Subject");
    }
}
