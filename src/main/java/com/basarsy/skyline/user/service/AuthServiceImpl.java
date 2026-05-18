package com.basarsy.skyline.user.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.InvalidTokenException;
import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetailsService;
import com.basarsy.skyline.user.dto.AuthResponse;
import com.basarsy.skyline.user.dto.LoginRequest;
import com.basarsy.skyline.user.dto.RefreshTokenRequest;
import com.basarsy.skyline.user.dto.RegisterRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SkylineUserDetailsService userDetailsService;

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
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            throw new SkylineException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        return buildAuthResponse(userDetailsService.loadUserByUsername(email));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        try {
            var refreshToken = request.refreshToken();
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new SkylineException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
            }

            var username = jwtService.extractUsername(refreshToken);
            var userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(refreshToken, userDetails)) {
                throw new SkylineException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
            }

            return buildAuthResponse(userDetails);
        } catch (InvalidTokenException ex) {
            throw new SkylineException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
    }

    private AuthResponse buildAuthResponse(org.springframework.security.core.userdetails.UserDetails userDetails) {
        var accessToken = jwtService.generateAccessToken(userDetails);
        var refreshToken = jwtService.generateRefreshToken(userDetails);
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenExpirySeconds());
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
