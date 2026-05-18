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
}
