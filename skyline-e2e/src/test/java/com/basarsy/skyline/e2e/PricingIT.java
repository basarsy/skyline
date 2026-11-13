package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.path.json.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every price travels with its currency (SKYLINE_CURRENCY, default EUR). */
class PricingIT extends E2eTest {

    static final String CURRENCY = System.getenv().getOrDefault("SKYLINE_CURRENCY", "EUR");

    @Test
    void flightsAndBookingsCarryTheCurrency() {
        String flightId = createFlight();
        assertThat((String) api(null).get("/flights/" + flightId).then().statusCode(200).extract().path("data.currency"))
                .isEqualTo(CURRENCY);

        User alice = registerPassenger("alice");
        JsonPath booking = api(alice.accessToken())
                .body(Map.of("flightId", flightId, "cabinClass", "BUSINESS",
                        "passenger", Map.of("firstName", "Alice", "lastName", "Smith", "passportNumber", "P1",
                                "dateOfBirth", "1990-01-01", "nationality", "Turkey")))
                .post("/reservations").then().statusCode(201).extract().jsonPath();

        assertThat(booking.getString("data.currency")).isEqualTo(CURRENCY);
        assertThat(booking.getString("data.flight.currency")).isEqualTo(CURRENCY);
        assertThat(booking.getDouble("data.totalPrice")).isEqualTo(250.0);

        String fetched = api(alice.accessToken()).get("/reservations/" + booking.getString("data.id"))
                .then().statusCode(200).extract().path("data.currency");
        assertThat(fetched).isEqualTo(CURRENCY);
    }
}
