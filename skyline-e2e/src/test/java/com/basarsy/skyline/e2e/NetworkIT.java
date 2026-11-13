package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

/** Airports and routes as the public sees them. */
class NetworkIT extends E2eTest {

    @Test
    void routesCarryTheIanaTimeZoneOfBothAirports() {
        String routeId = istToLhrRoute();

        JsonPath route = api(null).get("/routes/" + routeId).then().statusCode(200).extract().jsonPath();
        assertThat(route.getString("data.origin.timezone")).isEqualTo("Europe/Istanbul");
        assertThat(route.getString("data.destination.timezone")).isEqualTo("Europe/London");

        JsonPath all = api(null).get("/routes").then().statusCode(200).extract().jsonPath();
        assertThat(all.getList("data.origin.timezone")).doesNotContainNull();
        assertThat(all.getList("data.destination.timezone")).doesNotContainNull();
    }

    @Test
    void flightsEmbedTheRouteWithTimeZonesAndKeepTheUtcTimesSent() {
        String flightId = createFlight();

        JsonPath flight = api(null).get("/flights/" + flightId).then().statusCode(200).extract().jsonPath();
        assertThat(flight.getString("data.route.origin.timezone")).isEqualTo("Europe/Istanbul");
        assertThat(flight.getString("data.route.destination.timezone")).isEqualTo("Europe/London");
        // Times go back out exactly as sent: UTC, no offset.
        assertThat(flight.getString("data.departureTime")).doesNotContain("+").doesNotEndWith("Z");
    }
}
