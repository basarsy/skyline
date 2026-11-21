package com.basarsy.skyline.fleet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.common.config.SecurityConfig;
import com.basarsy.skyline.common.exception.GlobalExceptionHandler;
import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.security.InternalServiceAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtAccessDeniedHandler;
import com.basarsy.skyline.common.security.JwtAuthenticationEntryPoint;
import com.basarsy.skyline.common.security.JwtAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.fleet.dto.AircraftTypeSummary;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.service.AircraftService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Role rules and request validation of the aircraft endpoints, through the real security chain and real JWTs. */
@WebMvcTest(AircraftController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class, JwtAuthenticationFilter.class,
        InternalServiceAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AircraftControllerSecurityTest {

    private static final String INTERNAL_KEY = "test-internal-api-key-at-least-32-chars";
    private static final UUID ID = UUID.randomUUID();
    private static final String VALID_AIRCRAFT =
            "{\"tailNumber\":\"TC-SKY\",\"aircraftTypeId\":\"" + UUID.randomUUID() + "\",\"manufacturedYear\":2020}";
    private static final String VALID_STATUS = "{\"status\":\"MAINTENANCE\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AircraftService aircraftService;

    private final AircraftResponse aircraft = new AircraftResponse(ID, "TC-SKY",
            new AircraftTypeSummary(UUID.randomUUID(), "Airbus", "A320neo", 180), AircraftStatus.ACTIVE, 2020);

    @BeforeEach
    void setUp() {
        when(aircraftService.findAll()).thenReturn(List.of(aircraft));
        when(aircraftService.findById(ID)).thenReturn(aircraft);
        when(aircraftService.create(any())).thenReturn(aircraft);
        when(aircraftService.updateStatus(eq(ID), any())).thenReturn(aircraft);
    }

    // --- anonymous: nothing in fleet is public ---

    @Test
    void anonymousCallersGet401Everywhere() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/aircraft"),
                get("/api/v1/aircraft/" + ID),
                json(post("/api/v1/aircraft"), VALID_AIRCRAFT),
                json(patch("/api/v1/aircraft/" + ID + "/status"), VALID_STATUS))) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentication required"));
        }
        verifyNoInteractions(aircraftService);
    }

    @Test
    void aTamperedTokenIsTreatedAsAnonymous() throws Exception {
        mvc.perform(get("/api/v1/aircraft").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN") + "x"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(aircraftService);
    }

    // --- GET /aircraft ---

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_ADMIN"})
    void staffAndAdminCanListTheFleet(String role) throws Exception {
        mvc.perform(get("/api/v1/aircraft").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tailNumber").value("TC-SKY"));
    }

    @Test
    void passengersCannotListTheFleet() throws Exception {
        mvc.perform(get("/api/v1/aircraft").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftService);
    }

    @Test
    void internalServicesCannotListTheFleet() throws Exception {
        mvc.perform(get("/api/v1/aircraft").header(InternalServiceAuthenticationFilter.API_KEY_HEADER, INTERNAL_KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftService);
    }

    // --- GET /aircraft/{id} ---

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_ADMIN"})
    void staffAndAdminCanReadOneAircraft(String role) throws Exception {
        mvc.perform(get("/api/v1/aircraft/" + ID).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aircraftType.totalSeats").value(180));
    }

    @Test
    void internalServicesCanReadOneAircraftWithTheApiKey() throws Exception {
        mvc.perform(get("/api/v1/aircraft/" + ID).header(InternalServiceAuthenticationFilter.API_KEY_HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
    }

    @Test
    void aWrongApiKeyIsTreatedAsAnonymous() throws Exception {
        mvc.perform(get("/api/v1/aircraft/" + ID)
                        .header(InternalServiceAuthenticationFilter.API_KEY_HEADER, INTERNAL_KEY.replace('t', 'x')))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(aircraftService);
    }

    @Test
    void passengersCannotReadOneAircraft() throws Exception {
        mvc.perform(get("/api/v1/aircraft/" + ID).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftService);
    }

    @Test
    void unknownAircraftIsA404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(aircraftService.findById(unknown)).thenThrow(new ResourceNotFoundException("Aircraft not found: " + unknown));

        mvc.perform(get("/api/v1/aircraft/" + unknown).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Aircraft not found: " + unknown));
    }

    @Test
    void aNonUuidIdIsA400() throws Exception {
        mvc.perform(get("/api/v1/aircraft/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aircraftService);
    }

    // --- POST /aircraft ---

    @Test
    void adminCanAddAnAircraft() throws Exception {
        mvc.perform(json(post("/api/v1/aircraft"), VALID_AIRCRAFT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tailNumber").value("TC-SKY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_PASSENGER"})
    void onlyAdminCanAddAnAircraft(String role) throws Exception {
        mvc.perform(json(post("/api/v1/aircraft"), VALID_AIRCRAFT).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"tailNumber\":\" \",\"aircraftTypeId\":\"22222222-2222-2222-2222-222222222201\",\"manufacturedYear\":2020}",
            "{\"tailNumber\":\"TC-SKY\",\"manufacturedYear\":2020}",
            "{\"tailNumber\":\"TC-SKY\",\"aircraftTypeId\":\"22222222-2222-2222-2222-222222222201\",\"manufacturedYear\":1979}",
            "{\"tailNumber\":\"TC-SKY\",\"aircraftTypeId\":\"22222222-2222-2222-2222-222222222201\",\"manufacturedYear\":2101}"})
    void invalidAircraftIsA400WithFieldErrors(String body) throws Exception {
        mvc.perform(json(post("/api/v1/aircraft"), body).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        verifyNoInteractions(aircraftService);
    }

    @Test
    void malformedAircraftJsonIsA400() throws Exception {
        mvc.perform(json(post("/api/v1/aircraft"), "{\"tailNumber\":").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        verifyNoInteractions(aircraftService);
    }

    // --- PATCH /aircraft/{id}/status ---

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_ADMIN"})
    void staffAndAdminCanChangeTheStatus(String role) throws Exception {
        mvc.perform(json(patch("/api/v1/aircraft/" + ID + "/status"), VALID_STATUS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isOk());
    }

    @Test
    void passengersCannotChangeTheStatus() throws Exception {
        mvc.perform(json(patch("/api/v1/aircraft/" + ID + "/status"), VALID_STATUS)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftService);
    }

    @Test
    void aMissingStatusIsAValidationError() throws Exception {
        mvc.perform(json(patch("/api/v1/aircraft/" + ID + "/status"), "{}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("status: must not be null"));
        verifyNoInteractions(aircraftService);
    }

    @Test
    void anUnknownStatusIsA400() throws Exception {
        mvc.perform(json(patch("/api/v1/aircraft/" + ID + "/status"), "{\"status\":\"LOST\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        verifyNoInteractions(aircraftService);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String bearer(String role) {
        var user = new SkylineUserDetails(UUID.randomUUID(), "user@skyline.test", null,
                List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwtService.generateAccessToken(user);
    }
}
