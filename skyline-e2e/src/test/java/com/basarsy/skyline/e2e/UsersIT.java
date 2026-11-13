package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Current user, user lookup and staff accounts created by an admin. */
class UsersIT extends E2eTest {

    @Test
    void adminCreatesAStaffAccountThatCanLogInAndReachStaffEndpoints() {
        User staff = createStaff();

        api(staff.accessToken()).get("/aircraft").then().statusCode(200);

        JsonPath me = api(staff.accessToken()).get("/auth/me").then().statusCode(200).extract().jsonPath();
        assertThat(me.getString("data.id")).isEqualTo(staff.id());
        assertThat(me.getString("data.role")).isEqualTo("STAFF");
    }

    @Test
    void adminsFindUsersByEmail() {
        User staff = createStaff();
        User passenger = registerPassenger("findme");

        List<String> found = api(adminToken).queryParam("email", staff.email().toUpperCase()).get("/users")
                .then().statusCode(200).extract().path("data.content.id");
        assertThat(found).containsExactly(staff.id());

        List<String> passengers = api(adminToken).queryParam("email", passenger.email()).queryParam("role", "PASSENGER")
                .get("/users").then().statusCode(200).extract().path("data.content.id");
        assertThat(passengers).containsExactly(passenger.id());
    }

    @Test
    void meReturnsTheCaller() {
        User passenger = registerPassenger("me");

        JsonPath me = api(passenger.accessToken()).get("/auth/me").then().statusCode(200).extract().jsonPath();

        assertThat(me.getString("data.email")).isEqualTo(passenger.email());
        assertThat(me.getString("data.role")).isEqualTo("PASSENGER");
        api(null).get("/auth/me").then().statusCode(401);
    }

    @Test
    void onlyAdminsUseTheUserEndpoints() {
        User passenger = registerPassenger("pax");
        User staff = createStaff();

        api(passenger.accessToken()).get("/users").then().statusCode(403);
        api(staff.accessToken()).get("/users").then().statusCode(403);
        api(staff.accessToken()).body(Map.of("email", "x-" + shortId() + "@e2e.test", "password", STAFF_PASSWORD,
                "role", "ADMIN")).post("/users").then().statusCode(403);
    }

    @Test
    void duplicateEmailIsA409AndShortPasswordsA400() {
        User staff = createStaff();

        api(adminToken).body(Map.of("email", staff.email(), "password", STAFF_PASSWORD, "role", "STAFF"))
                .post("/users").then().statusCode(409);
        api(adminToken).body(Map.of("email", "short-" + shortId() + "@e2e.test", "password", "only8chr", "role", "STAFF"))
                .post("/users").then().statusCode(400);
    }
}
