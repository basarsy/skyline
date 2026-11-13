package com.basarsy.skyline.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.basarsy.skyline.user.dto.CreateUserRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.service.AuthService;
import com.basarsy.skyline.user.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** /auth/me and /users through the real security chain with real JWTs. */
@WebMvcTest({AuthController.class, UserController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class, JwtAuthenticationFilter.class,
        InternalServiceAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-at-least-32-characters-long",
        "skyline.internal.api-key=test-internal-api-key-at-least-32-chars"})
class UserEndpointsSecurityTest {

    private static final UUID ME = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @Test
    void meReturnsTheCaller() throws Exception {
        when(userService.getCurrentUser(ME)).thenReturn(new UserResponse(ME, "me@skyline.test", UserRole.PASSENGER));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_PASSENGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ME.toString()))
                .andExpect(jsonPath("$.data.role").value("PASSENGER"));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void adminsCanSearchUsers() throws Exception {
        when(userService.searchUsers(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/users").param("email", "ops").param("role", "STAFF")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isOk());
        verify(userService).searchUsers(eq("ops"), eq(UserRole.STAFF), any());
    }

    @Test
    void staffAndPassengersCannotUseTheUserEndpoints() throws Exception {
        for (String role : List.of("ROLE_STAFF", "ROLE_PASSENGER")) {
            mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"x@skyline.test\",\"password\":\"long-enough-password\",\"role\":\"STAFF\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void adminCreatesAStaffAccount() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.createUser(new CreateUserRequest("ops@skyline.test", "long-enough-password", UserRole.STAFF)))
                .thenReturn(new UserResponse(id, "ops@skyline.test", UserRole.STAFF));

        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ops@skyline.test\",\"password\":\"long-enough-password\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void shortPasswordsAreRejectedWithAFieldError() throws Exception {
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ops@skyline.test\",\"password\":\"short-pw\",\"role\":\"STAFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("password: ")));
        verify(userService, org.mockito.Mockito.never()).createUser(isNull());
    }

    private String bearer(String role) {
        var user = new SkylineUserDetails(ME, "me@skyline.test", null, List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwtService.generateAccessToken(user);
    }
}
