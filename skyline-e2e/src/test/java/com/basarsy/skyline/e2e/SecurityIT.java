package com.basarsy.skyline.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecurityIT extends E2eTest {

    @Test
    void gatewayStripsTheInternalServiceKey() {
        // Even with the right key, an external request must arrive without it (no ROLE_SERVICE).
        api(null)
                .header("X-Skyline-Internal-Key", System.getenv().getOrDefault("SKYLINE_INTERNAL_API_KEY", "guess"))
                .put("/flights/" + UUID.randomUUID() + "/inventory/increment")
                .then().statusCode(401);
    }

    @Test
    void passengersCannotUseStaffEndpoints() {
        User passenger = registerPassenger("pax");

        api(passenger.accessToken()).get("/aircraft").then().statusCode(403);
        api(passenger.accessToken()).delete("/flights/" + UUID.randomUUID()).then().statusCode(403);
    }

    @Test
    void disabledAccountCannotLogInOrRefresh() throws Exception {
        User user = registerPassenger("disabled");

        try (var connection = DriverManager.getConnection(AUTH_DB_URL, "skyline_user", "skyline_pass");
                var statement = connection.prepareStatement("UPDATE users SET is_enabled = false WHERE email = ?")) {
            statement.setString(1, user.email());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        String message = login(user.email(), PASSWORD).then().statusCode(401).extract().path("message");
        assertThat(message).as("same answer as a wrong password").isEqualTo("Invalid email or password");
        api(null).body(Map.of("refreshToken", user.refreshToken())).post("/auth/refresh").then().statusCode(401);
    }

    @Test
    void malformedInputIsA400NotA500() {
        api(adminToken).body("{\"bad json").post("/crew").then().statusCode(400);
        api(adminToken).get("/flights/not-a-uuid/crew").then().statusCode(400);
    }

    @Test
    void duplicateDataIsA409NotA500() {
        String tail = "TC-" + shortId();
        Map<String, Object> aircraft = Map.of("tailNumber", tail, "aircraftTypeId", A320NEO, "manufacturedYear", 2020);
        api(adminToken).body(aircraft).post("/aircraft").then().statusCode(201);
        api(adminToken).body(aircraft).post("/aircraft").then().statusCode(409);
    }
}
