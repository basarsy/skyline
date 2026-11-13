package com.basarsy.skyline.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.common.config.SecurityConfig;
import com.basarsy.skyline.common.exception.GlobalExceptionHandler;
import com.basarsy.skyline.common.security.InternalServiceAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtAccessDeniedHandler;
import com.basarsy.skyline.common.security.JwtAuthenticationEntryPoint;
import com.basarsy.skyline.common.security.JwtAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.service.FlightService;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Routing and role rules of the flight endpoints, through the real security chain and real JWTs. */
@WebMvcTest(FlightController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class, JwtAuthenticationFilter.class,
        InternalServiceAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class FlightControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private FlightService flightService;

    @BeforeEach
    void setUp() {
        when(flightService.getFlightBoard(any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void staffCanReadTheBoardWithDefaultPagingAndSort() throws Exception {
        mvc.perform(get("/api/v1/flights/board").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<FlightBoardFilter> filter = ArgumentCaptor.forClass(FlightBoardFilter.class);
        verify(flightService).getFlightBoard(filter.capture(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "departureTime"));
        assertThat(filter.getValue()).isEqualTo(new FlightBoardFilter(null, null, null, null));
    }

    @Test
    void boardParsesEveryFilterIncludingRepeatedStatus() throws Exception {
        UUID routeId = UUID.randomUUID();
        mvc.perform(get("/api/v1/flights/board").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN"))
                        .param("date", "2030-06-01")
                        .param("status", "DELAYED").param("status", "BOARDING")
                        .param("flightNumber", "sk1")
                        .param("routeId", routeId.toString())
                        .param("page", "2").param("size", "10").param("sort", "departureTime,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<FlightBoardFilter> filter = ArgumentCaptor.forClass(FlightBoardFilter.class);
        verify(flightService).getFlightBoard(filter.capture(), pageable.capture());
        assertThat(filter.getValue()).isEqualTo(new FlightBoardFilter(LocalDate.of(2030, 6, 1),
                EnumSet.of(FlightStatus.DELAYED, FlightStatus.BOARDING), "sk1", routeId));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "departureTime"));
    }

    @Test
    void passengersGet403OnTheBoard() throws Exception {
        mvc.perform(get("/api/v1/flights/board").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(flightService);
    }

    @Test
    void anonymousCallersGet401OnTheBoard() throws Exception {
        mvc.perform(get("/api/v1/flights/board"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        verifyNoInteractions(flightService);
    }

    @Test
    void boardIsNotMistakenForAFlightIdAndIdsStillWork() throws Exception {
        UUID id = UUID.randomUUID();
        FlightResponse flight = FlightResponse.builder().id(id).flightNumber("SK1").build();
        when(flightService.getFlight(id)).thenReturn(flight);

        mvc.perform(get("/api/v1/flights/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flightNumber").value("SK1"));
        mvc.perform(get("/api/v1/flights/board").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidStatusIsA400() throws Exception {
        mvc.perform(get("/api/v1/flights/board").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STAFF"))
                        .param("status", "LOST"))
                .andExpect(status().isBadRequest());
    }

    private String bearer(String role) {
        var user = new SkylineUserDetails(UUID.randomUUID(), "user@skyline.test", null,
                List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwtService.generateAccessToken(user);
    }
}
