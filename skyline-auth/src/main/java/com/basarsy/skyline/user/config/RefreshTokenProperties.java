package com.basarsy.skyline.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param reuseGrace how long after a rotation the old refresh token may still be presented (e.g. by a second tab
 *     refreshing at the same moment) and only be rejected; after that a reuse is treated as theft
 */
@ConfigurationProperties(prefix = "skyline.auth.refresh-token")
public record RefreshTokenProperties(Duration reuseGrace) {

    public static final Duration DEFAULT_REUSE_GRACE = Duration.ofSeconds(30);

    public RefreshTokenProperties {
        if (reuseGrace == null) {
            reuseGrace = DEFAULT_REUSE_GRACE;
        }
    }
}
