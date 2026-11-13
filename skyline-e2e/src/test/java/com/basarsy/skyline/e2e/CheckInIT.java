package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckInIT extends E2eTest {

    @Test
    void passengersGetSequentialRealSeatsAndTheSameGate() {
        String flightId = createFlight();
        User alice = registerPassenger("alice");
        User bob = registerPassenger("bob");
        Booking aliceBooking = book(alice, flightId, "Alice", "Smith", "P1");
        Booking bobBooking = book(bob, flightId, "Bob", "Jones", "P2");

        var alicePass = checkIn(alice, aliceBooking).then().statusCode(200).extract().jsonPath();
        var bobPass = checkIn(bob, bobBooking).then().statusCode(200).extract().jsonPath();

        assertThat(alicePass.getString("data.seatNumber")).isEqualTo("1A");
        assertThat(bobPass.getString("data.seatNumber")).isEqualTo("1B");
        assertThat(bobPass.getString("data.gate")).isEqualTo(alicePass.getString("data.gate"));
        assertThat(alicePass.getString("data.origin")).isEqualTo("IST");
        assertThat(alicePass.getString("data.destination")).isEqualTo("LHR");
    }

    @Test
    void repeatingCheckInReturnsTheSameBoardingPass() {
        User alice = registerPassenger("alice");
        Booking booking = book(alice, createFlight(), "Alice", "Smith", "P1");

        String first = checkIn(alice, booking).then().statusCode(200).extract().path("data.barcodeData");
        String second = checkIn(alice, booking).then().statusCode(200).extract().path("data.barcodeData");

        assertThat(second).isEqualTo(first);
        String fetched = api(alice.accessToken()).get("/checkin/" + booking.pnr() + "/boarding-pass")
                .then().statusCode(200).extract().path("data.barcodeData");
        assertThat(fetched).isEqualTo(first);
    }

    @Test
    void onlyTheOwnerOrStaffCanCheckIn() {
        User alice = registerPassenger("alice");
        User mallory = registerPassenger("mallory");
        Booking booking = book(alice, createFlight(), "Alice", "Smith", "P1");

        checkIn(mallory, booking).then().statusCode(404);
        api(mallory.accessToken()).get("/checkin/" + booking.pnr() + "/boarding-pass").then().statusCode(404);

        api(adminToken).body(Map.of("pnr", booking.pnr())).post("/checkin").then().statusCode(200);
    }

    @Test
    void internalStatusEndpointIsGoneAndInternalCheckInIsNotReachableFromOutside() {
        User alice = registerPassenger("alice");
        Booking booking = book(alice, createFlight(), "Alice", "Smith", "P1");

        assertThat(api(adminToken).queryParam("status", "CHECKED_IN").put("/reservations/" + booking.id() + "/status")
                .statusCode()).isNotEqualTo(200);
        // ROLE_SERVICE only: a user token (even admin) must not reach it.
        api(adminToken).post("/reservations/" + booking.id() + "/check-in").then().statusCode(403);
    }

    private static io.restassured.response.Response checkIn(User user, Booking booking) {
        return api(user.accessToken()).body(Map.of("pnr", booking.pnr())).post("/checkin");
    }
}
