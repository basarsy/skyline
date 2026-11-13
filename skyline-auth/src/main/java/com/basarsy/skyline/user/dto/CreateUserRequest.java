package com.basarsy.skyline.user.dto;

import com.basarsy.skyline.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An account created by an admin. Only STAFF and ADMIN can be created this way (passengers register
 * themselves), and the password rule matches the bootstrap admin's: at least 12 characters.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotNull UserRole role) {}
