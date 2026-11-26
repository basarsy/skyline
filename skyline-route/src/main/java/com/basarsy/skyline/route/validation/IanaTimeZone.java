package com.basarsy.skyline.route.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

/**
 * An IANA time zone id the JVM knows, such as {@code Europe/Istanbul}. Search days, check-in windows and every
 * local time in a message are worked out in the airport's zone; an unknown one ("Europe/Istambul", "UTC+3")
 * used to be stored and then silently read as UTC everywhere. {@code null} is valid (pair with @NotBlank).
 */
@Documented
@Constraint(validatedBy = IanaTimeZone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimeZone {

    String message() default "must be an IANA time zone such as Europe/Istanbul";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<IanaTimeZone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ZoneId.getAvailableZoneIds().contains(value);
        }
    }
}
