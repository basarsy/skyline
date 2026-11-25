package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Flight search and the operations flight board. */
class FlightsIT extends E2eTest {

    @Test
    void delayedFlightsAreFoundBySearch() {
        String flightId = createFlight();
        delay(flightId);

        assertThat(searchIstLhr(istanbulDepartureDate(flightId))).contains(flightId);
    }

    @Test
    void searchDatesAreTheOriginAirportsCalendarDays() {
        String aircraftId = api(adminToken)
                .body(Map.of("tailNumber", "TC-" + shortId(), "aircraftTypeId", A320NEO, "manufacturedYear", 2020))
                .post("/aircraft").then().statusCode(201).extract().path("data.id");
        // 22:30 UTC is 01:30 the next day in Istanbul (UTC+3).
        LocalDate utcDay = nowUtc().toLocalDate().plusDays(3);
        String flightId = createFlight(aircraftId, utcDay.atTime(22, 30));

        assertThat(searchIstLhr(utcDay.plusDays(1).toString())).contains(flightId);
        assertThat(searchIstLhr(utcDay.toString())).doesNotContain(flightId);
        // The board has no origin airport: its dates stay UTC days.
        assertThat(board(adminToken, utcDay.toString())).contains(flightId);
    }

    @Test
    void staffSeeEveryStatusOnTheBoardForADay() {
        User staff = createStaff();
        String scheduled = createFlight();
        String cancelled = createFlight();
        api(adminToken).delete("/flights/" + cancelled).then().statusCode(200);

        LocalDateTime departure = departureUtc(scheduled);

        assertThat(board(staff.accessToken(), departure.toLocalDate().toString())).contains(scheduled, cancelled);
        assertThat(searchIstLhr(istanbulDate(departure))).as("search stays bookable-only")
                .contains(scheduled).doesNotContain(cancelled);
    }

    @Test
    void boardFiltersCombine() {
        String delayed = createFlight();
        String scheduled = createFlight();
        delay(delayed);
        String number = api(null).get("/flights/" + delayed).then().extract().path("data.flightNumber");

        List<String> ids = api(adminToken)
                .queryParam("date", utcDepartureDate(delayed))
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

    @Test
    void aircraftInMaintenanceCannotBeScheduled() {
        String tail = "TC-" + shortId();
        String aircraftId = api(adminToken)
                .body(Map.of("tailNumber", tail, "aircraftTypeId", A320NEO, "manufacturedYear", 2020))
                .post("/aircraft").then().statusCode(201).extract().path("data.id");
        api(adminToken).body(Map.of("status", "MAINTENANCE")).patch("/aircraft/" + aircraftId + "/status")
                .then().statusCode(200);

        String departure = nowUtc().plusDays(3).withNano(0).toString();
        String message = api(adminToken)
                .body(Map.of("flightNumber", flightNumber(), "routeId", istToLhrRoute(),
                        "aircraftId", aircraftId, "departureTime", departure,
                        "arrivalTime", nowUtc().plusDays(3).plusHours(4).withNano(0).toString(), "basePrice", 100))
                .post("/flights").then().statusCode(400).extract().path("message");

        assertThat(message).isEqualTo("Aircraft " + tail.toUpperCase() + " is not active");
    }

    static List<String> searchIstLhr(String date) {
        return api(null)
                .queryParam("origin", "IST").queryParam("destination", "LHR").queryParam("date", date)
                .queryParam("size", 500)
                .get("/flights").then().statusCode(200).extract().path("data.content.id");
    }

    static List<String> board(String token, String date) {
        return api(token).queryParam("date", date).queryParam("size", 500)
                .get("/flights/board").then().statusCode(200).extract().path("data.content.id");
    }

    /** Delays the flight by 45 minutes (a delay always comes with its expected time). */
    static void delay(String flightId) {
        String estimate = departureUtc(flightId).plusMinutes(45).toString();
        api(adminToken).body(Map.of("estimatedDepartureTime", estimate)).put("/flights/" + flightId + "/delay")
                .then().statusCode(200);
        assertThat(api(null).get("/flights/" + flightId).then().extract().<String>path("data.status"))
                .isEqualTo("DELAYED");
    }

    static LocalDateTime departureUtc(String flightId) {
        String departure = api(null).get("/flights/" + flightId).then().statusCode(200).extract().path("data.departureTime");
        return LocalDateTime.parse(departure);
    }

    /** The departure's UTC day, the flight board's {@code date}. */
    static String utcDepartureDate(String flightId) {
        return departureUtc(flightId).toLocalDate().toString();
    }

    /** The departure's day in Istanbul, the origin of IST → LHR: the search {@code date}. */
    static String istanbulDepartureDate(String flightId) {
        return istanbulDate(departureUtc(flightId));
    }

    static String istanbulDate(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDate().toString();
    }
}
