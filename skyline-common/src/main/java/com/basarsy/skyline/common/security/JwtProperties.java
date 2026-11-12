package com.basarsy.skyline.common.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The signing secret has no default: anyone who knows it can mint ADMIN tokens, so
 * startup fails if it is missing or shorter than the 256 bits HS256 requires.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenExpiryMs, long refreshTokenExpiryMs) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not set. Provide it via the JWT_SECRET environment variable.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long.");
        }
    }
}
