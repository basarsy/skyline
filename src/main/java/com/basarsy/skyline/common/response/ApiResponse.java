package com.basarsy.skyline.common.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        LocalDateTime timestamp,
        List<String> errors) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, LocalDateTime.now(), null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now(), null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now(), null);
    }

    public static ApiResponse<Void> errors(List<String> errors) {
        return new ApiResponse<>(false, "Validation failed", null, LocalDateTime.now(), errors);
    }
}
