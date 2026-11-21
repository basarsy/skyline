package com.basarsy.skyline.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * The string's UTF-8 encoding is at most {@link #value()} bytes. BCrypt only looks at the first 72 bytes and
 * Spring's encoder rejects longer passwords outright, so passwords carry {@code @MaxBytes(72)}. A character count
 * ({@code @Size}) is not enough: non-ASCII characters take up to four bytes each. {@code null} is valid.
 */
@Documented
@Constraint(validatedBy = MaxBytes.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

    /** The BCrypt input limit. */
    int BCRYPT = 72;

    int value();

    String message() default "must be at most {value} bytes (UTF-8)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxBytes, CharSequence> {

        private int max;

        @Override
        public void initialize(MaxBytes annotation) {
            max = annotation.value();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            return value == null || value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
