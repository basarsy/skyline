package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** Staff-side operations: flight cancellation over Kafka and crew manifests. */
class OperationsIT extends E2eTest {

    @Test
    void cancellingAFlightCancelsItsReservationsAsynchronously() {
        String flightId = createFlight();
        User alice = registerPassenger("alice");
        Booking booking = book(alice, flightId, "Alice", "Smith", "P1");

        api(adminToken).delete("/flights/" + flightId).then().statusCode(200);

        // schedule -> Kafka -> reservation is eventually consistent.
        Awaitility.await("reservation cancelled via Kafka")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat((String) api(alice.accessToken()).get("/reservations/" + booking.id())
                        .then().statusCode(200).extract().path("data.status")).isEqualTo("CANCELLED"));
    }

    @Test
    void cancellingThroughTheStatusPatchRunsTheFullCancellation() {
        String flightId = createFlight();
        User alice = registerPassenger("alice");
        Booking booking = book(alice, flightId, "Alice", "Smith", "P1");
        assertThat(availableSeats(flightId)).isEqualTo(179);

        api(adminToken).body(Map.of("status", "CANCELLED")).patch("/flights/" + flightId + "/status")
                .then().statusCode(200);

        assertThat(availableSeats(flightId)).as("seats reset like DELETE does").isEqualTo(180);
        Awaitility.await("reservation cancelled via Kafka")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat((String) api(alice.accessToken()).get("/reservations/" + booking.id())
                        .then().statusCode(200).extract().path("data.status")).isEqualTo("CANCELLED"));
    }

    @Test
    void staffCannotCancelAFlightThroughTheStatusPatch() {
        String flightId = createFlight();
        User staff = createStaff();

        String message = api(staff.accessToken()).body(Map.of("status", "CANCELLED"))
                .patch("/flights/" + flightId + "/status").then().statusCode(403).extract().path("message");

        assertThat(message).isEqualTo("Only an admin can cancel a flight");
        assertThat((String) api(null).get("/flights/" + flightId).then().extract().path("data.status"))
                .isEqualTo("SCHEDULED");
    }

    @Test
    void crewManifestReflectsAssignmentsImmediately() {
        String flightId = createFlight();
        User captainUser = registerPassenger("captain");
        String crewMemberId = api(adminToken)
                .body(Map.of(
                        "userId", captainUser.id(),
                        "employeeNumber", "E" + shortId(),
                        "firstName", "Cap", "lastName", "Tain",
                        "role", "CAPTAIN",
                        "licenseNumber", "L" + shortId(),
                        "licenseExpiryDate", "2035-01-01"))
                .post("/crew").then().statusCode(200).extract().path("data.id");

        assertThat(manifest(flightId)).isEmpty(); // also primes the cache

        api(adminToken).body(Map.of("crewMemberId", crewMemberId, "role", "CAPTAIN"))
                .post("/flights/" + flightId + "/crew").then().statusCode(200);
        assertThat(manifest(flightId)).as("assignment must evict the cached manifest").hasSize(1);

        api(adminToken).delete("/flights/" + flightId + "/crew/" + crewMemberId).then().statusCode(200);
        assertThat(manifest(flightId)).as("removal must evict the cached manifest").isEmpty();
    }

    private static List<?> manifest(String flightId) {
        return api(adminToken).get("/flights/" + flightId + "/crew").then().statusCode(200).extract().path("data");
    }
}
