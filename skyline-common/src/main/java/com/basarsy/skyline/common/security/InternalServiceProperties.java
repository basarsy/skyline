package com.basarsy.skyline.common.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret that services present to each other in {@code X-Skyline-Internal-Key}. It grants
 * {@code ROLE_SERVICE}, so there is deliberately no default: startup fails if it is missing or weak.
 */
@ConfigurationProperties(prefix = "skyline.internal")
public record InternalServiceProperties(String apiKey) {

    static final int MIN_LENGTH = 32;

    public InternalServiceProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "skyline.internal.api-key is not set. Provide it via the SKYLINE_INTERNAL_API_KEY environment variable.");
        }
        if (apiKey.getBytes(StandardCharsets.UTF_8).length < MIN_LENGTH) {
            throw new IllegalStateException(
                    "skyline.internal.api-key must be at least " + MIN_LENGTH + " bytes long.");
        }
    }
}
