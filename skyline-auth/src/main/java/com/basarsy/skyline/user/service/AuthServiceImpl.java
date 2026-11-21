package com.basarsy.skyline.user.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.InvalidTokenException;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.user.config.RefreshTokenProperties;
import com.basarsy.skyline.user.dto.AuthResponse;
import com.basarsy.skyline.user.dto.LoginRequest;
import com.basarsy.skyline.user.dto.RefreshTokenRequest;
import com.basarsy.skyline.user.dto.RegisterRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.user.entity.RefreshToken;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.RefreshTokenRepository;
import com.basarsy.skyline.user.repository.UserRepository;
import com.basarsy.skyline.user.security.SkylineUserDetails;
import com.basarsy.skyline.user.security.SkylineUserDetailsService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SkylineUserDetailsService userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;
    private final Clock clock;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        var email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new SkylineException("Email is already registered", HttpStatus.CONFLICT);
        }

        var user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.PASSENGER);
        user.setEnabled(true);

        var saved = userRepository.save(user);
        return toUserResponse(saved);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        var email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            // Bad password, unknown user and disabled account all get the same answer (no account enumeration).
            // BadCredentialsException alone let DisabledException escape.
            throw new SkylineException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        // A login starts a new token family.
        return issueTokens(userDetailsService.loadUserByUsername(email), UUID.randomUUID());
    }

    /**
     * Rotates the refresh token: the presented one is revoked and a new one is issued in the same family. Not
     * rolled back on a 401, so a theft-triggered family revocation sticks.
     */
    @Override
    @Transactional(noRollbackFor = SkylineException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        var refreshToken = request.refreshToken();
        String username;
        UUID tokenId;
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw invalidRefreshToken();
            }
            username = jwtService.extractUsername(refreshToken);
            tokenId = jwtService.extractTokenId(refreshToken);
        } catch (InvalidTokenException ex) { // bad signature, malformed or expired
            throw invalidRefreshToken();
        }
        if (tokenId == null) { // issued before refresh tokens were tracked
            throw invalidRefreshToken();
        }

        var stored = refreshTokenRepository.findById(tokenId).orElseThrow(AuthServiceImpl::invalidRefreshToken);
        var now = LocalDateTime.now(clock);
        if (stored.getRevokedAt() != null) {
            onReuse(stored, now);
            throw invalidRefreshToken();
        }

        var userDetails = userDetailsService.loadUserByUsername(username);
        if (!userDetails.isEnabled() || !userDetails.getId().equals(stored.getUserId())) {
            // A disabled account must not be able to mint new access tokens with an old refresh token.
            throw invalidRefreshToken();
        }
        if (refreshTokenRepository.markReplaced(tokenId, now) == 0) {
            // A concurrent refresh with the same token won the race a moment ago: same as a reuse within the grace.
            throw invalidRefreshToken();
        }
        // Roles and the enabled flag come fresh from the database, not from the old token.
        return issueTokens(userDetails, stored.getFamilyId());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        if (request == null || !StringUtils.hasText(request.refreshToken())) {
            return;
        }
        UUID tokenId;
        try {
            if (!jwtService.isRefreshToken(request.refreshToken())) {
                return;
            }
            tokenId = jwtService.extractTokenId(request.refreshToken());
        } catch (InvalidTokenException ex) {
            return;
        }
        if (tokenId == null) {
            return;
        }
        // The whole family (this login session), so a token a second tab rotated a moment ago dies too.
        refreshTokenRepository.findById(tokenId).ifPresent(
                token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), LocalDateTime.now(clock)));
    }

    /**
     * A revoked token came back. Shortly after its rotation that is a concurrent refresh (another tab); later it
     * means the token leaked, so every token of that login is revoked, the thief's included.
     */
    private void onReuse(RefreshToken token, LocalDateTime now) {
        var replacedAt = token.getReplacedAt();
        if (replacedAt == null || !replacedAt.plus(refreshTokenProperties.reuseGrace()).isBefore(now)) {
            return; // revoked by logout or an earlier reuse, or rotated within the grace window
        }
        int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
        log.warn("Refresh token reuse detected for user {}: revoked {} token(s) of family {}",
                token.getUserId(), revoked, token.getFamilyId());
    }

    private AuthResponse issueTokens(SkylineUserDetails userDetails, UUID familyId) {
        var now = LocalDateTime.now(clock);
        var tokenId = UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(
                tokenId, userDetails.getId(), familyId, now, now.plus(jwtService.refreshTokenExpiry())));

        var accessToken = jwtService.generateAccessToken(userDetails);
        var refreshToken = jwtService.generateRefreshToken(userDetails, tokenId);
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenExpirySeconds());
    }

    private static SkylineException invalidRefreshToken() {
        return new SkylineException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
