package com.basarsy.skyline.user.security;

import com.basarsy.skyline.user.entity.User;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class SkylineUserDetails extends com.basarsy.skyline.common.security.SkylineUserDetails {

    private final boolean enabled;

    public SkylineUserDetails(User user) {
        super(
            user.getId(),
            user.getEmail(),
            user.getPasswordHash(),
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        this.enabled = user.isEnabled();
    }

    /** Reflects users.is_enabled so Spring Security rejects disabled accounts at login. */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
