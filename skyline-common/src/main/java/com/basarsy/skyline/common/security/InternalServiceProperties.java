package com.basarsy.skyline.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "skyline.internal")
public record InternalServiceProperties(
        @DefaultValue("dev-internal-api-key-change-me") String apiKey) {}
