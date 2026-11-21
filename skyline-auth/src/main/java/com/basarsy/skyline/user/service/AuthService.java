package com.basarsy.skyline.user.service;

import com.basarsy.skyline.user.dto.AuthResponse;
import com.basarsy.skyline.user.dto.LoginRequest;
import com.basarsy.skyline.user.dto.RefreshTokenRequest;
import com.basarsy.skyline.user.dto.RegisterRequest;
import com.basarsy.skyline.user.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    /** Revokes the refresh token's session. A no-op for a missing, unknown, invalid or expired token. */
    void logout(RefreshTokenRequest request);
}
