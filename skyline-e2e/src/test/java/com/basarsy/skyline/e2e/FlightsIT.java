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

    @Test
    void staffSeeEveryStatusOnTheBoardForADay() {
        User staff = createStaff();
        String scheduled = createFlight();
        String cancelled = createFlight();
        api(adminToken).delete("/flights/" + cancelled).then().statusCode(200);
        String date = departureDate(scheduled);

        List<String> ids = api(staff.accessToken()).queryParam("date", date).queryParam("size", 500)
                .get("/flights/board").then().statusCode(200).extract().path("data.content.id");

        assertThat(ids).contains(scheduled, cancelled);
        assertThat(searchIstLhr(date)).as("search stays bookable-only").contains(scheduled).doesNotContain(cancelled);
    }

    @Test
    void boardFiltersCombine() {
        String delayed = createFlight();
        String scheduled = createFlight();
        api(adminToken).body(Map.of("status", "DELAYED")).patch("/flights/" + delayed + "/status").then().statusCode(200);
        String number = api(null).get("/flights/" + delayed).then().extract().path("data.flightNumber");

        List<String> ids = api(adminToken)
                .queryParam("date", departureDate(delayed))
                .queryParam("status", "DELAYED").queryParam("status", "BOARDING")
                .queryParam("flightNumber", number.toLowerCase())
                .queryParam("routeId", istToLhrRoute())
                .get("/flights/board").then().statusCode(200).extract().path("data.content.id");

        assertThat(ids).containsExactly(delayed);
        assertThat(ids).doesNotContain(scheduled);
    }

    @Test
    void boardIsForStaffOnly() {
        User passenger = registerPassenger("pax");
        api(passenger.accessToken()).get("/flights/board").then().statusCode(403);
        api(null).get("/flights/board").then().statusCode(401);
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
