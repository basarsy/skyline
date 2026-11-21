package com.basarsy.skyline.fleet.controller;

import com.basarsy.skyline.common.config.SecurityConfig;
import com.basarsy.skyline.common.exception.GlobalExceptionHandler;
import com.basarsy.skyline.common.security.InternalServiceAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtAccessDeniedHandler;
import com.basarsy.skyline.common.security.JwtAuthenticationEntryPoint;
import com.basarsy.skyline.common.security.JwtAuthenticationFilter;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.fleet.service.AircraftService;
import com.basarsy.skyline.fleet.service.AircraftTypeService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared slice for the fleet controller tests: both controllers, the real security chain and real JWTs.
 * Keeping the configuration identical lets every subclass reuse one cached Spring context.
 */
@WebMvcTest({AircraftController.class, AircraftTypeController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class, JwtAuthenticationFilter.class,
        InternalServiceAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
abstract class FleetWebMvcTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    protected AircraftService aircraftService;

    @MockitoBean
    protected AircraftTypeService aircraftTypeService;

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Authorization header value: a freshly signed access token carrying the given role. */
    protected String bearer(String role) {
        var user = new SkylineUserDetails(UUID.randomUUID(), "user@skyline.test", null,
                List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwtService.generateAccessToken(user);
    }
}
