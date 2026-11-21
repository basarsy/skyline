package com.basarsy.skyline.user.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MaxBytesTest {

    private record Password(@MaxBytes(MaxBytes.BCRYPT) String value) {}

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void countsUtf8BytesNotCharacters() {
        assertThat(validator.validate(new Password("a".repeat(72)))).isEmpty();
        assertThat(validator.validate(new Password("a".repeat(73)))).hasSize(1);
        assertThat(validator.validate(new Password("€".repeat(24)))).as("72 bytes").isEmpty();
        assertThat(validator.validate(new Password("€".repeat(25)))).as("75 bytes").hasSize(1);
        assertThat(validator.validate(new Password(null))).isEmpty();
    }
}
