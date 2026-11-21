package com.basarsy.skyline.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param reuseGrace how long after a rotation the old refresh token may still be presented (e.g. by a second tab
 *     refreshing at the same moment) and only be rejected; after that a reuse is treated as theft
 */
@ConfigurationProperties(prefix = "skyline.auth.refresh-token")
public record RefreshTokenProperties(@DefaultValue("30s") Duration reuseGrace) {}
