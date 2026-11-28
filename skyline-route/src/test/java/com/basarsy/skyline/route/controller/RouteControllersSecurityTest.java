package com.basarsy.skyline.route.controller;

import static org.mockito.ArgumentMatchers.any;
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
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.InternalServiceAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtAccessDeniedHandler;
import com.basarsy.skyline.common.security.JwtAuthenticationEntryPoint;
import com.basarsy.skyline.common.security.JwtAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.dto.AirportSummary;
import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.route.service.AirportService;
import com.basarsy.skyline.route.service.RouteService;
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

/** Airports and routes are publicly readable but only admins may add them; checked through the real security chain. */
@WebMvcTest({AirportController.class, RouteController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class, JwtAuthenticationFilter.class,
        InternalServiceAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class RouteControllersSecurityTest {

    private static final UUID AIRPORT_ID = UUID.randomUUID();
    private static final UUID ROUTE_ID = UUID.randomUUID();
    private static final String VALID_AIRPORT = """
            {"iataCode":"SAW","icaoCode":"LTFJ","name":"Sabiha Gokcen","city":"Istanbul",
             "country":"Turkey","timezone":"Europe/Istanbul"}""";
    private static final String VALID_ROUTE = "{\"originAirportId\":\"" + UUID.randomUUID()
            + "\",\"destinationAirportId\":\"" + UUID.randomUUID() + "\",\"distanceKm\":2500,\"estimatedDurationMinutes\":240}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AirportService airportService;

    @MockitoBean
    private RouteService routeService;

    private final AirportResponse airport =
            new AirportResponse(AIRPORT_ID, "SAW", "LTFJ", "Sabiha Gokcen", "Istanbul", "Turkey", "Europe/Istanbul");
    private final RouteResponse route = new RouteResponse(ROUTE_ID,
            new AirportSummary(UUID.randomUUID(), "IST", "Istanbul Airport", "Istanbul", "Turkey", "Europe/Istanbul"),
            new AirportSummary(UUID.randomUUID(), "LHR", "Heathrow Airport", "London", "United Kingdom", "Europe/London"),
            2500, 240);

    @BeforeEach
    void setUp() {
        when(airportService.findAll()).thenReturn(List.of(airport));
        when(airportService.findById(AIRPORT_ID)).thenReturn(airport);
        when(airportService.create(any())).thenReturn(airport);
        when(routeService.findAll()).thenReturn(List.of(route));
        when(routeService.findById(ROUTE_ID)).thenReturn(route);
        when(routeService.findByOriginAndDestination("IST", "LHR")).thenReturn(List.of(route));
        when(routeService.create(any())).thenReturn(route);
    }

    // --- reads are public ---

    @Test
    void anonymousCallersCanReadAirports() throws Exception {
        mvc.perform(get("/api/v1/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].iataCode").value("SAW"));
        mvc.perform(get("/api/v1/airports/" + AIRPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Europe/Istanbul"));
    }

    @Test
    void anonymousCallersCanReadAndSearchRoutes() throws Exception {
        mvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ROUTE_ID.toString()));
        mvc.perform(get("/api/v1/routes/" + ROUTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.destination.iataCode").value("LHR"));
        mvc.perform(get("/api/v1/routes/search").param("originIata", "IST").param("destinationIata", "LHR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].distanceKm").value(2500));
    }

    @Test
    void searchWithoutBothAirportsIsA400NotARouteIdLookup() throws Exception {
        mvc.perform(get("/api/v1/routes/search").param("originIata", "IST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'destinationIata'"));
        verifyNoInteractions(routeService);
    }

    @Test
    void nonUuidIdsAreA400() throws Exception {
        mvc.perform(get("/api/v1/airports/IST")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/routes/IST-LHR")).andExpect(status().isBadRequest());
        verifyNoInteractions(airportService, routeService);
    }

    @Test
    void unknownAirportsAndRoutesAreA404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(airportService.findById(unknown)).thenThrow(new ResourceNotFoundException("Airport not found: " + unknown));
        when(routeService.findById(unknown)).thenThrow(new ResourceNotFoundException("Route not found: " + unknown));

        mvc.perform(get("/api/v1/airports/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Airport not found: " + unknown));
        mvc.perform(get("/api/v1/routes/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Route not found: " + unknown));
    }

    // --- writes are admin only ---

    @Test
    void anonymousCallersCannotAddAirportsOrRoutes() throws Exception {
        mvc.perform(json(post("/api/v1/airports"), VALID_AIRPORT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mvc.perform(json(post("/api/v1/routes"), VALID_ROUTE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(airportService, routeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_PASSENGER", "ROLE_STAFF"})
    void nonAdminsCannotAddAirportsOrRoutes(String role) throws Exception {
        mvc.perform(json(post("/api/v1/airports"), VALID_AIRPORT).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        mvc.perform(json(post("/api/v1/routes"), VALID_ROUTE).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(airportService, routeService);
    }

    @Test
    void adminCanAddAnAirport() throws Exception {
        mvc.perform(json(post("/api/v1/airports"), VALID_AIRPORT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(AIRPORT_ID.toString()));
    }

    @Test
    void onlyAnAdminCorrectsATimeZoneAndOnlyToAValidOne() throws Exception {
        when(airportService.updateTimezone(AIRPORT_ID, "Europe/Istanbul")).thenReturn(airport);
        String path = "/api/v1/airports/" + AIRPORT_ID + "/timezone";

        mvc.perform(json(patch(path), "{\"timezone\":\"Europe/Istanbul\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch(path), "{\"timezone\":\"Europe/Istambul\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(patch(path), "{\"timezone\":\"Europe/Istanbul\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Europe/Istanbul"));
    }

    @Test
    void adminCanAddARoute() throws Exception {
        mvc.perform(json(post("/api/v1/routes"), VALID_ROUTE).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(ROUTE_ID.toString()));
    }

    // --- validation ---

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"iataCode\":\"SA\",\"icaoCode\":\"LTFJ\",\"name\":\"n\",\"city\":\"c\",\"country\":\"c\",\"timezone\":\"UTC\"}",
            "{\"iataCode\":\"SAWX\",\"icaoCode\":\"LTFJ\",\"name\":\"n\",\"city\":\"c\",\"country\":\"c\",\"timezone\":\"UTC\"}",
            "{\"iataCode\":\"SAW\",\"icaoCode\":\"LTF\",\"name\":\"n\",\"city\":\"c\",\"country\":\"c\",\"timezone\":\"UTC\"}",
            "{\"iataCode\":\"SAW\",\"icaoCode\":\"LTFJ\",\"name\":\" \",\"city\":\"c\",\"country\":\"c\",\"timezone\":\"UTC\"}",
            "{\"iataCode\":\"SAW\",\"icaoCode\":\"LTFJ\",\"name\":\"n\",\"city\":\"c\",\"country\":\"c\"}",
            // An unknown zone was stored, then silently read as UTC; a fixed offset knows no daylight saving.
            "{\"iataCode\":\"SAW\",\"icaoCode\":\"LTFJ\",\"name\":\"n\",\"city\":\"c\",\"country\":\"c\",\"timezone\":\"Europe/Istambul\"}",
            "{\"iataCode\":\"SAW\",\"icaoCode\":\"LTFJ\",\"name\":\"n\",\"city\":\"c\",\"country\":\"c\",\"timezone\":\"UTC+3\"}"})
    void invalidAirportIsA400(String body) throws Exception {
        mvc.perform(json(post("/api/v1/airports"), body).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        verifyNoInteractions(airportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"destinationAirportId\":\"11111111-1111-1111-1111-111111111102\",\"distanceKm\":2500,\"estimatedDurationMinutes\":240}",
            "{\"originAirportId\":\"11111111-1111-1111-1111-111111111101\",\"distanceKm\":2500,\"estimatedDurationMinutes\":240}",
            "{\"originAirportId\":\"11111111-1111-1111-1111-111111111101\",\"destinationAirportId\":\"11111111-1111-1111-1111-111111111102\",\"distanceKm\":0,\"estimatedDurationMinutes\":240}",
            "{\"originAirportId\":\"11111111-1111-1111-1111-111111111101\",\"destinationAirportId\":\"11111111-1111-1111-1111-111111111102\",\"distanceKm\":2500,\"estimatedDurationMinutes\":0}"})
    void invalidRouteIsA400(String body) throws Exception {
        mvc.perform(json(post("/api/v1/routes"), body).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        verifyNoInteractions(routeService);
    }

    @Test
    void aMalformedAirportIdInARouteIsA400() throws Exception {
        mvc.perform(json(post("/api/v1/routes"), "{\"originAirportId\":\"IST\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        verifyNoInteractions(routeService);
    }

    @Test
    void duplicateAirportIsA409() throws Exception {
        when(airportService.create(any()))
                .thenThrow(new SkylineException("Airport with IATA code already exists", HttpStatus.CONFLICT));

        mvc.perform(json(post("/api/v1/airports"), VALID_AIRPORT).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Airport with IATA code already exists"));
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
