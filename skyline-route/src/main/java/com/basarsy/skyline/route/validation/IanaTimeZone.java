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
import java.util.Set;

/**
 * An IANA region time zone the JVM knows, such as {@code Europe/Istanbul}. Search days, check-in windows and every
 * local time in a message are worked out in the airport's zone. An unknown id ("Europe/Istambul") used to be
 * stored and then silently read as UTC everywhere; a fixed offset ("UTC+3", "+03:00") parses, but knows no
 * daylight saving time, so half the year an airport like London's would be an hour off. {@code null} is valid
 * (pair with @NotBlank).
 */
@Documented
@Constraint(validatedBy = IanaTimeZone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimeZone {

    String message() default "must be an IANA time zone such as Europe/Istanbul, not a fixed offset";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<IanaTimeZone, String> {

        /** The region ids (getAvailableZoneIds copies the whole list on every call). */
        private static final Set<String> REGIONS = Set.copyOf(ZoneId.getAvailableZoneIds());

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || isRegion(value);
        }

        /** Whether {@code zone} is an IANA region id this JVM knows. */
        public static boolean isRegion(String zone) {
            return REGIONS.contains(zone);
        }
    }
}
