package com.basarsy.skyline.fleet.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The calling user's roles, for rules that depend on more than the endpoint's @PreAuthorize. */
final class CallerRoles {

    private CallerRoles() {
    }

    static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
