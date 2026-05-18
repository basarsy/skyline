package com.basarsy.skyline.user.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
