package com.basarsy.skyline.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.JwtProperties;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.user.config.RefreshTokenProperties;
import com.basarsy.skyline.user.dto.AuthResponse;
import com.basarsy.skyline.user.dto.LoginRequest;
import com.basarsy.skyline.user.dto.RefreshTokenRequest;
import com.basarsy.skyline.user.entity.RefreshToken;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.RefreshTokenRepository;
import com.basarsy.skyline.user.repository.UserRepository;
import com.basarsy.skyline.user.security.SkylineUserDetails;
import com.basarsy.skyline.user.security.SkylineUserDetailsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private static final String EMAIL = "pax@test.local";
    private static final UUID USER_ID = UUID.randomUUID();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final JwtService jwtService =
            new JwtService(new JwtProperties("test-secret-key-at-least-32-characters-long", 60_000, 600_000));
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final SkylineUserDetailsService userDetailsService = mock(SkylineUserDetailsService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    /** In-memory stand-in for the refresh_tokens table, with the same semantics as the repository's queries. */
    private final Map<UUID, RefreshToken> tokens = new HashMap<>();
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            userRepository, mock(PasswordEncoder.class), authenticationManager, jwtService,
            userDetailsService, refreshTokenRepository, new RefreshTokenProperties(Duration.ofSeconds(30)), clock);

    @BeforeEach
    void inMemoryRefreshTokens() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            tokens.put(token.getId(), token);
            return token;
        });
        when(refreshTokenRepository.findById(any()))
                .thenAnswer(inv -> Optional.ofNullable(tokens.get(inv.<UUID>getArgument(0))));
        when(refreshTokenRepository.markReplaced(any(), any())).thenAnswer(inv -> update(
                t -> Objects.equals(t.getId(), inv.getArgument(0)), t -> {
                    t.setRevokedAt(inv.getArgument(1));
                    t.setReplacedAt(inv.getArgument(1));
                }));
        when(refreshTokenRepository.revokeFamily(any(), any())).thenAnswer(inv -> update(
                t -> Objects.equals(t.getFamilyId(), inv.getArgument(0)), t -> t.setRevokedAt(inv.getArgument(1))));
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails(true));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true)));
    }

    private int update(Predicate<RefreshToken> match, Consumer<RefreshToken> change) {
        var active = tokens.values().stream().filter(t -> t.getRevokedAt() == null).filter(match).toList();
        active.forEach(change);
        return active.size();
    }

    @Test
    void loginOfDisabledAccountIsRejectedLikeBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Passw0rd!!")))
                .isInstanceOfSatisfying(SkylineException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void loginStoresTheRefreshTokenInANewFamily() {
        AuthResponse first = login();
        AuthResponse second = login();

        RefreshToken a = stored(first.refreshToken());
        RefreshToken b = stored(second.refreshToken());
        assertThat(a.getUserId()).isEqualTo(USER_ID);
        assertThat(a.getRevokedAt()).isNull();
        assertThat(a.getExpiresAt()).isEqualTo(a.getIssuedAt().plus(Duration.ofMillis(600_000)));
        assertThat(a.getFamilyId()).isNotEqualTo(b.getFamilyId());
    }

    @Test
    void refreshRotatesTheTokenWithinItsFamily() {
        AuthResponse login = login();

        AuthResponse refreshed = refresh(login.refreshToken());

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        RefreshToken old = stored(login.refreshToken());
        RefreshToken next = stored(refreshed.refreshToken());
        assertThat(old.getRevokedAt()).isNotNull();
        assertThat(old.getReplacedAt()).isNotNull();
        assertThat(next.getRevokedAt()).isNull();
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId());
    }

    @Test
    void reuseWithinTheGraceWindowIsRejectedButRevokesNothingElse() {
        AuthResponse login = login();
        AuthResponse refreshed = refresh(login.refreshToken());
        clock.advance(Duration.ofSeconds(29));

        assertUnauthorized(() -> refresh(login.refreshToken()));

        assertThat(stored(refreshed.refreshToken()).getRevokedAt()).isNull();
        assertThat(refresh(refreshed.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void reuseAfterTheGraceWindowRevokesTheWholeFamily() {
        AuthResponse otherSession = login();
        AuthResponse login = login();
        AuthResponse refreshed = refresh(login.refreshToken());
        clock.advance(Duration.ofSeconds(31));

        assertUnauthorized(() -> refresh(login.refreshToken()));

        assertThat(stored(refreshed.refreshToken()).getRevokedAt()).isNotNull();
        assertUnauthorized(() -> refresh(refreshed.refreshToken()));
        assertThat(stored(otherSession.refreshToken()).getRevokedAt()).as("other logins are left alone").isNull();
    }

    @Test
    void losingAConcurrentRotationIsRejected() {
        AuthResponse login = login();
        when(refreshTokenRepository.markReplaced(any(), any())).thenReturn(0);

        assertUnauthorized(() -> refresh(login.refreshToken()));
    }

    @Test
    void refreshIsRejectedOnceTheAccountIsDisabled() {
        AuthResponse login = login();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(false)));

        assertUnauthorized(() -> refresh(login.refreshToken()));
        assertThat(stored(login.refreshToken()).getRevokedAt()).as("not rotated").isNull();
    }

    @Test
    void untrackedOrForeignRefreshTokensAreRejected() {
        SkylineUserDetails user = userDetails(true);

        assertUnauthorized(() -> refresh(jwtService.generateRefreshToken(user, UUID.randomUUID())));
        assertUnauthorized(() -> refresh(jwtService.generateAccessToken(user)));
        assertUnauthorized(() -> refresh("not-a-jwt"));
    }

    @Test
    void logoutRevokesTheSessionAndIsIdempotent() {
        AuthResponse login = login();
        AuthResponse refreshed = refresh(login.refreshToken());

        authService.logout(new RefreshTokenRequest(refreshed.refreshToken()));
        authService.logout(new RefreshTokenRequest(refreshed.refreshToken()));

        assertThat(stored(refreshed.refreshToken()).getRevokedAt()).isNotNull();
        assertUnauthorized(() -> refresh(refreshed.refreshToken()));
    }

    @Test
    void logoutIgnoresMissingAndInvalidTokens() {
        SkylineUserDetails user = userDetails(true);
        for (RefreshTokenRequest request : new RefreshTokenRequest[] {
                null,
                new RefreshTokenRequest(null),
                new RefreshTokenRequest(" "),
                new RefreshTokenRequest("not-a-jwt"),
                new RefreshTokenRequest(jwtService.generateAccessToken(user)),
                new RefreshTokenRequest(jwtService.generateRefreshToken(user, UUID.randomUUID()))}) {
            assertThatCode(() -> authService.logout(request)).doesNotThrowAnyException();
        }
    }

    private AuthResponse login() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        return authService.login(new LoginRequest(EMAIL, "Passw0rd!!"));
    }

    private AuthResponse refresh(String refreshToken) {
        return authService.refresh(new RefreshTokenRequest(refreshToken));
    }

    private RefreshToken stored(String refreshToken) {
        return tokens.get(jwtService.refreshTokenId(refreshToken).orElseThrow());
    }

    private static void assertUnauthorized(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SkylineException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static SkylineUserDetails userDetails(boolean enabled) {
        return new SkylineUserDetails(user(enabled));
    }

    private static User user(boolean enabled) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setPasswordHash("hash");
        user.setRole(UserRole.PASSENGER);
        user.setEnabled(enabled);
        return user;
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
