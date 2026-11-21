package com.basarsy.skyline.user.dto;

import com.basarsy.skyline.user.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) @MaxBytes(MaxBytes.BCRYPT) String password) {}
