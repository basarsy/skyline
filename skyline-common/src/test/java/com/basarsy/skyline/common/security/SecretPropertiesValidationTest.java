package com.basarsy.skyline.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretPropertiesValidationTest {

    private static final String STRONG = "0123456789abcdef0123456789abcdef";

    @Test
    void jwtSecretIsRequiredAndMustBeAtLeast256Bits() {
        assertThatThrownBy(() -> new JwtProperties(null, 1, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("  ", 1, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("too-short", 1, 1)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new JwtProperties(STRONG, 1, 1)).doesNotThrowAnyException();
    }

    @Test
    void internalApiKeyIsRequiredAndMustBeLongEnough() {
        assertThatThrownBy(() -> new InternalServiceProperties(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InternalServiceProperties("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InternalServiceProperties("short")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new InternalServiceProperties(STRONG)).doesNotThrowAnyException();
    }
}
