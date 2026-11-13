package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingFlowIT extends E2eTest {

    @Test
    void publicSearchFindsAScheduledFlightWithRouteAndAircraft() {
        String flightId = createFlight();
        String date = LocalDate.now().plusDays(1).toString();

        List<String> found = api(null)
                .queryParam("origin", "IST").queryParam("destination", "LHR").queryParam("date", date)
                .queryParam("size", 500)
                .get("/flights").then().statusCode(200).extract().path("data.content.id");
        assertThat(found).contains(flightId);

        var flight = api(null).get("/flights/" + flightId).then().statusCode(200).extract().jsonPath();
        assertThat(flight.getString("data.status")).isEqualTo("SCHEDULED");
        assertThat(flight.getString("data.route.origin.iataCode")).isEqualTo("IST");
        assertThat(flight.getInt("data.aircraft.aircraftType.totalSeats")).isEqualTo(180);
    }

    @Test
    void bookingHoldsASeatAndCancellingReturnsIt() {
        String flightId = createFlight();
        User alice = registerPassenger("alice");

        Booking booking = book(alice, flightId, "Alice", "Smith", "P111111111");
        assertThat(booking.pnr()).as("6 chars, no ambiguous 0/O/1/I").matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(availableSeats(flightId)).isEqualTo(179);

        api(alice.accessToken()).delete("/reservations/" + booking.id()).then().statusCode(200);
        assertThat(availableSeats(flightId)).isEqualTo(180);
    }

    @Test
    void eachBookingKeepsItsOwnPassengerDetails() {
        String flightId = createFlight();
        User alice = registerPassenger("alice");

        Booking forHerself = book(alice, flightId, "Alice", "Smith", "P111111111");
        book(alice, flightId, "Charlie", "Smith", "P222222222");

        String firstName = api(alice.accessToken()).get("/reservations/" + forHerself.id())
                .then().statusCode(200).extract().path("data.passenger.firstName");
        assertThat(firstName).as("second booking must not overwrite the first one's passenger").isEqualTo("Alice");

        List<String> mine = api(alice.accessToken()).get("/reservations/my")
                .then().statusCode(200).extract().path("data.content.passenger.firstName");
        assertThat(mine).containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void newUserWithoutBookingsGetsAnEmptyPage() {
        User newcomer = registerPassenger("newcomer");

        List<?> mine = api(newcomer.accessToken()).get("/reservations/my")
                .then().statusCode(200).extract().path("data.content");
        assertThat(mine).isEmpty();
    }
}
