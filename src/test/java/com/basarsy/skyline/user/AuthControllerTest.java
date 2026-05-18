package com.basarsy.skyline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("skyline")
            .withUsername("skyline_user")
            .withPassword("skyline_pass");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void registerLoginAndRefresh() throws Exception {
        var registerBody =
                """
                {"email":"passenger@example.com","password":"password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("passenger@example.com"))
                .andExpect(jsonPath("$.data.role").value("PASSENGER"));

        var loginBody =
                """
                {"email":"passenger@example.com","password":"password123"}
                """;

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        var loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        var refreshToken = loginJson.path("data").path("refreshToken").asText();

        var refreshBody = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        createUser("user@example.com", "password123", UserRole.PASSENGER);

        var loginBody =
                """
                {"email":"user@example.com","password":"wrong-password"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void protectedAirportCreateRequiresAdminRole() throws Exception {
        createUser("passenger@example.com", "password123", UserRole.PASSENGER);
        createUser("admin@example.com", "password123", UserRole.ADMIN);

        var passengerToken = loginAndGetAccessToken("passenger@example.com", "password123");
        var adminToken = loginAndGetAccessToken("admin@example.com", "password123");

        var airportBody =
                """
                {
                  "iataCode":"AAA",
                  "icaoCode":"KAAA",
                  "name":"Test Airport",
                  "city":"Test City",
                  "country":"TC",
                  "timezone":"UTC"
                }
                """;

        mockMvc.perform(post("/api/v1/airports")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(airportBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/airports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(airportBody))
                .andExpect(status().isCreated());
    }

    @Test
    void publicAirportListDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/airports")).andExpect(status().isOk());
    }

    private void createUser(String email, String password, UserRole role) {
        var user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        var loginBody = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        var token = json.path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
