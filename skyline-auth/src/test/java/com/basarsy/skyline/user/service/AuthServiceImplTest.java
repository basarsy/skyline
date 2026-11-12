package com.basarsy.skyline.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.JwtProperties;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.user.dto.LoginRequest;
import com.basarsy.skyline.user.dto.RefreshTokenRequest;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import com.basarsy.skyline.user.security.SkylineUserDetails;
import com.basarsy.skyline.user.security.SkylineUserDetailsService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private final JwtService jwtService =
            new JwtService(new JwtProperties("test-secret-key-at-least-32-characters-long", 60_000, 600_000));
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final SkylineUserDetailsService userDetailsService = mock(SkylineUserDetailsService.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            mock(UserRepository.class), mock(PasswordEncoder.class), authenticationManager, jwtService, userDetailsService);

    @Test
    void loginOfDisabledAccountIsRejectedLikeBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("pax@test.local", "Passw0rd!!")))
                .isInstanceOfSatisfying(SkylineException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void refreshIsRejectedOnceTheAccountIsDisabled() {
        SkylineUserDetails enabled = userDetails(true);
        String refreshToken = jwtService.generateRefreshToken(enabled);
        when(userDetailsService.loadUserByUsername("pax@test.local")).thenReturn(userDetails(false));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(refreshToken)))
                .isInstanceOfSatisfying(SkylineException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refreshStillWorksForEnabledAccount() {
        SkylineUserDetails enabled = userDetails(true);
        String refreshToken = jwtService.generateRefreshToken(enabled);
        when(userDetailsService.loadUserByUsername("pax@test.local")).thenReturn(enabled);

        assertThat(authService.refresh(new RefreshTokenRequest(refreshToken)).accessToken()).isNotBlank();
    }

    private static SkylineUserDetails userDetails(boolean enabled) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("pax@test.local");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PASSENGER);
        user.setEnabled(enabled);
        return new SkylineUserDetails(user);
    }
}
