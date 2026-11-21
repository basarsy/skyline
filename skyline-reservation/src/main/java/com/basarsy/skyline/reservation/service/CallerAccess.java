package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.reservation.entity.Reservation;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is calling, and whether they may touch a reservation: its owner, or STAFF/ADMIN. */
final class CallerAccess {

    private CallerAccess() {}

    /**
     * The calling user's id. Not every authenticated caller is a user: an internal service authenticated by
     * API key has a plain string principal, and that is "not allowed" (403), not a server error.
     */
    static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new SkylineException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        if (!(authentication.getPrincipal() instanceof SkylineUserDetails user) || user.getId() == null) {
            throw new SkylineException("This operation requires a user account", HttpStatus.FORBIDDEN);
        }
        return user.getId();
    }

    static boolean isStaffOrAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    static void verifyOwnership(Reservation reservation) {
        if (!isStaffOrAdmin() && !currentUserId().equals(reservation.getPassenger().getUserId())) {
            throw new SkylineException("You do not have permission to access this reservation", HttpStatus.FORBIDDEN);
        }
    }
}
