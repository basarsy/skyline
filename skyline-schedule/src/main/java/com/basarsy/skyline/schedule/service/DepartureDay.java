package com.basarsy.skyline.schedule.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;

/**
 * A calendar day at an airport as the UTC departure-time range it covers: {@code [start, end)}, both UTC
 * wall-clock times like every stored {@code LocalDateTime}. A day is 24 hours except on DST changes (23 or 25).
 */
@Slf4j
public record DepartureDay(LocalDateTime start, LocalDateTime end) {

    /** {@code date} in the airport zone {@code zoneId}; a missing or unknown zone counts as UTC. */
    public static DepartureDay of(LocalDate date, String zoneId) {
        ZoneId zone = zoneOrUtc(zoneId);
        return new DepartureDay(utc(date, zone), utc(date.plusDays(1), zone));
    }

    private static LocalDateTime utc(LocalDate date, ZoneId zone) {
        // atStartOfDay(zone), not atTime(00:00): a zone whose DST gap swallows midnight starts the day at 01:00.
        return date.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static ZoneId zoneOrUtc(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            log.warn("Origin airport has no time zone; searching its date as a UTC day");
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            log.warn("Origin airport time zone '{}' is not valid; searching its date as a UTC day", zoneId);
            return ZoneOffset.UTC;
        }
    }
}
