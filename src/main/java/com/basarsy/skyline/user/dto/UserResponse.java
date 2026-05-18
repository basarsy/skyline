package com.basarsy.skyline.user.dto;

import com.basarsy.skyline.user.entity.UserRole;
import java.util.UUID;

public record UserResponse(UUID id, String email, UserRole role) {}
