package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** "Manage booking" lookups: owner/staff see everything, others need PNR + last name and get a redacted view. */
class PnrLookupIT extends E2eTest {

    private User owner;
    private Booking booking;

    @BeforeEach
    void bookAFlight() {
        owner = registerPassenger("owner");
        booking = book(owner, createFlight(), "Ada", "Lovelace", "P123456789");
    }

    @Test
    void anonymousLookupWithoutOrWithWrongLastNameLooksLikeAnUnknownPnr() {
        api(null).get("/reservations/pnr/" + booking.pnr()).then().statusCode(404);
        api(null).queryParam("lastName", "Wrong").get("/reservations/pnr/" + booking.pnr()).then().statusCode(404);
        String unknownPnrMessage = api(null).queryParam("lastName", "Lovelace").get("/reservations/pnr/ZZZZZZ")
                .then().statusCode(404).extract().path("message");
        String wrongNameMessage = api(null).queryParam("lastName", "Wrong").get("/reservations/pnr/" + booking.pnr())
                .then().extract().path("message");
        assertThat(wrongNameMessage).isEqualTo(unknownPnrMessage);
    }

    @Test
    void anonymousLookupWithLastNameIsRedacted() {
        var passenger = api(null).queryParam("lastName", "lovelace").get("/reservations/pnr/" + booking.pnr().toLowerCase())
                .then().statusCode(200).extract().jsonPath();

        String passport = "P123456789";
        String expectedMask = "*".repeat(passport.length() - 3) + passport.substring(passport.length() - 3);
        assertThat(passenger.getString("data.passenger.passportNumber")).isEqualTo(expectedMask);
        assertThat(passenger.getString("data.passenger.dateOfBirth")).isNull();
        assertThat(passenger.getString("data.passenger.userId")).isNull();
    }

    @Test
    void ownerAndAdminSeeTheFullReservation() {
        String ownerView = api(owner.accessToken()).get("/reservations/pnr/" + booking.pnr())
                .then().statusCode(200).extract().path("data.passenger.passportNumber");
        String adminView = api(adminToken).get("/reservations/pnr/" + booking.pnr())
                .then().statusCode(200).extract().path("data.passenger.passportNumber");

        assertThat(ownerView).isEqualTo("P123456789");
        assertThat(adminView).isEqualTo("P123456789");
    }

    @Test
    void anotherPassengerCannotReadSomeoneElsesReservation() {
        User stranger = registerPassenger("stranger");

        api(stranger.accessToken()).get("/reservations/pnr/" + booking.pnr()).then().statusCode(404);
        api(stranger.accessToken()).get("/reservations/" + booking.id()).then().statusCode(403);
    }
}
