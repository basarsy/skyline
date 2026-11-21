package com.basarsy.skyline.fleet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.common.config.SecurityConfig;
import com.basarsy.skyline.common.exception.GlobalExceptionHandler;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.InternalServiceAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtAccessDeniedHandler;
import com.basarsy.skyline.common.security.JwtAuthenticationEntryPoint;
import com.basarsy.skyline.common.security.JwtAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import com.basarsy.skyline.fleet.service.AircraftTypeService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Role rules and request validation of the aircraft-type endpoints, through the real security chain. */
@WebMvcTest(AircraftTypeController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class, JwtAuthenticationFilter.class,
        InternalServiceAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AircraftTypeControllerSecurityTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String VALID_TYPE =
            "{\"manufacturer\":\"Airbus\",\"model\":\"A321neo\",\"totalSeats\":220,\"cabinConfig\":\"Y220\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AircraftTypeService aircraftTypeService;

    private final AircraftTypeResponse type = new AircraftTypeResponse(ID, "Airbus", "A321neo", 220, "Y220");

    @BeforeEach
    void setUp() {
        when(aircraftTypeService.findAll()).thenReturn(List.of(type));
        when(aircraftTypeService.findById(ID)).thenReturn(type);
        when(aircraftTypeService.create(any())).thenReturn(type);
    }

    @Test
    void anonymousCallersGet401Everywhere() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/aircraft-types"),
                get("/api/v1/aircraft-types/" + ID),
                json(post("/api/v1/aircraft-types"), VALID_TYPE))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(aircraftTypeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_ADMIN"})
    void staffAndAdminCanReadTypes(String role) throws Exception {
        mvc.perform(get("/api/v1/aircraft-types").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].model").value("A321neo"));
        mvc.perform(get("/api/v1/aircraft-types/" + ID).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSeats").value(220));
    }

    @Test
    void passengersCannotReadTypes() throws Exception {
        mvc.perform(get("/api/v1/aircraft-types").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/aircraft-types/" + ID).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftTypeService);
    }

    @Test
    void adminCanAddAType() throws Exception {
        mvc.perform(json(post("/api/v1/aircraft-types"), VALID_TYPE).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_PASSENGER"})
    void onlyAdminCanAddAType(String role) throws Exception {
        mvc.perform(json(post("/api/v1/aircraft-types"), VALID_TYPE).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aircraftTypeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"manufacturer\":\"\",\"model\":\"A321neo\",\"totalSeats\":220}",
            "{\"manufacturer\":\"Airbus\",\"model\":\" \",\"totalSeats\":220}",
            "{\"manufacturer\":\"Airbus\",\"model\":\"A321neo\",\"totalSeats\":0}"})
    void invalidTypeIsA400(String body) throws Exception {
        mvc.perform(json(post("/api/v1/aircraft-types"), body).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        verifyNoInteractions(aircraftTypeService);
    }

    @Test
    void duplicateTypeIsA409() throws Exception {
        when(aircraftTypeService.create(any()))
                .thenThrow(new SkylineException("Aircraft type already exists", HttpStatus.CONFLICT));

        mvc.perform(json(post("/api/v1/aircraft-types"), VALID_TYPE).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Aircraft type already exists"));
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
