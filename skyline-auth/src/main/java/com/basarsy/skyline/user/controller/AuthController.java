package com.basarsy.skyline.user.controller;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.user.dto.AuthResponse;
import com.basarsy.skyline.user.dto.LoginRequest;
import com.basarsy.skyline.user.dto.RefreshTokenRequest;
import com.basarsy.skyline.user.dto.RegisterRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.user.service.AuthService;
import com.basarsy.skyline.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        var user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    /** Always 204: logging out with an unknown, invalid or expired token (or none) is not an error. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /** The caller's account; any authenticated user (non-POST /auth/** requires a token). */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal SkylineUserDetails principal) {
        // Null when the caller is not a user, e.g. a service authenticated by the internal key.
        if (principal == null || principal.getId() == null) {
            throw new SkylineException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return ApiResponse.success(userService.getCurrentUser(principal.getId()));
    }
}
