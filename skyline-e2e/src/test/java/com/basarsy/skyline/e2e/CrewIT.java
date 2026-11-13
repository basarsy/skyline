package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The crew directory used to pick crew members without knowing their ids. */
class CrewIT extends E2eTest {

    @Test
    void createdCrewMembersAreListedAndFilterable() {
        String tag = shortId();
        String captain = createCrewMember("Ada", "Lovelace" + tag, "CAPTAIN", "C" + tag);
        String officer = createCrewMember("Grace", "Hopper" + tag, "FIRST_OFFICER", "F" + tag);

        List<String> byTag = api(adminToken).queryParam("q", tag.toLowerCase()).get("/crew")
                .then().statusCode(200).extract().path("data.id");
        assertThat(byTag).as("sorted by last name: Hopper before Lovelace").containsExactly(officer, captain);

        List<String> captains = api(adminToken).queryParam("q", tag).queryParam("role", "CAPTAIN").get("/crew")
                .then().statusCode(200).extract().path("data.id");
        assertThat(captains).containsExactly(captain);

        User staff = createStaff();
        String employeeNumber = api(staff.accessToken()).get("/crew/" + captain)
                .then().statusCode(200).extract().path("data.employeeNumber");
        assertThat(employeeNumber).isEqualTo("C" + tag);
    }

    @Test
    void duplicateEmployeeNumberIsA409() {
        String employeeNumber = "D" + shortId();
        createCrewMember("Ada", "One", "PURSER", employeeNumber);

        String message = api(adminToken).body(crewMember("Bea", "Two", "PURSER", employeeNumber.toLowerCase(),
                        registerPassenger("crew").id()))
                .post("/crew").then().statusCode(409).extract().path("message");

        assertThat(message).isEqualTo("Employee number already exists");
    }

    @Test
    void passengersCannotSeeTheCrewDirectory() {
        User passenger = registerPassenger("pax");

        api(passenger.accessToken()).get("/crew").then().statusCode(403);
        api(null).get("/crew").then().statusCode(401);
    }

    static String createCrewMember(String first, String last, String role, String employeeNumber) {
        return api(adminToken).body(crewMember(first, last, role, employeeNumber, registerPassenger("crew").id()))
                .post("/crew").then().statusCode(200).extract().path("data.id");
    }

    static Map<String, Object> crewMember(String first, String last, String role, String employeeNumber, String userId) {
        return Map.of("userId", userId, "employeeNumber", employeeNumber, "firstName", first, "lastName", last,
                "role", role, "licenseNumber", "L" + shortId(), "licenseExpiryDate", "2035-01-01");
    }
}
