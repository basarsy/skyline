package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Flight search and the operations flight board. */
class FlightsIT extends E2eTest {

    @Test
    void delayedFlightsAreFoundBySearch() {
        String flightId = createFlight();
        api(adminToken).body(Map.of("status", "DELAYED")).patch("/flights/" + flightId + "/status")
                .then().statusCode(200);

        assertThat(searchIstLhr(departureDate(flightId))).contains(flightId);
    }

    static List<String> searchIstLhr(String date) {
        return api(null)
                .queryParam("origin", "IST").queryParam("destination", "LHR").queryParam("date", date)
                .queryParam("size", 500)
                .get("/flights").then().statusCode(200).extract().path("data.content.id");
    }

    static String departureDate(String flightId) {
        String departure = api(null).get("/flights/" + flightId).then().statusCode(200).extract().path("data.departureTime");
        return departure.substring(0, 10);
    }
}
