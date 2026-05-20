package com.basarsy.skyline.fleet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
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
class FleetControllerTest {

  private static final String AIRBUS_A320_ID = "22222222-2222-2222-2222-222222222201";

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("skyline")
          .withUsername("skyline_user")
          .withPassword("skyline_pass");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private AircraftRepository aircraftRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private String staffToken;
  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    aircraftRepository.deleteAll();
    userRepository.deleteAll();

    createUser("staff@example.com", UserRole.STAFF);
    createUser("admin@example.com", UserRole.ADMIN);
    staffToken = login("staff@example.com");
    adminToken = login("admin@example.com");
  }

  @Test
  void listAircraftRequiresStaffRole() throws Exception {
    mockMvc.perform(get("/api/v1/aircraft")).andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/v1/aircraft").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void adminCanCreateAircraftAndStaffCanUpdateStatus() throws Exception {
    var createBody =
        """
        {
          "tailNumber":"tc-sky1",
          "aircraftTypeId":"%s",
          "manufacturedYear":2020
        }
        """
            .formatted(AIRBUS_A320_ID);

    var createResult =
        mockMvc
            .perform(
                post("/api/v1/aircraft")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.tailNumber").value("TC-SKY1"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn();

    var aircraftId =
        objectMapper
            .readTree(createResult.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asText();

    var statusBody = """
        {"status":"MAINTENANCE"}
        """;

    mockMvc
        .perform(
            patch("/api/v1/aircraft/" + aircraftId + "/status")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("MAINTENANCE"));
  }

  @Test
  void staffCannotCreateAircraft() throws Exception {
    var createBody =
        """
        {
          "tailNumber":"tc-sky2",
          "aircraftTypeId":"%s",
          "manufacturedYear":2019
        }
        """
            .formatted(AIRBUS_A320_ID);

    mockMvc
        .perform(
            post("/api/v1/aircraft")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateAircraftType() throws Exception {
    var body =
        """
        {
          "manufacturer":"Embraer",
          "model":"E195-E2",
          "totalSeats":146,
          "cabinConfig":"Y146"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/aircraft-types")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.manufacturer").value("Embraer"));
  }

  private void createUser(String email, UserRole role) {
    var user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("password123"));
    user.setRole(role);
    user.setEnabled(true);
    userRepository.save(user);
  }

  private String login(String email) throws Exception {
    var body = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "password123"));
    var result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("accessToken")
        .asText();
  }
}
