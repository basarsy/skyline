package com.basarsy.skyline.schedule.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * A calendar day at an airport as the UTC departure-time range it covers: {@code [start, end)}, both UTC
 * wall-clock times like every stored {@code LocalDateTime}. A day is 24 hours except on DST changes (23 or 25).
 */
@Slf4j
public record DepartureDay(LocalDateTime start, LocalDateTime end) {

    /**
     * Airport zone values ("" for none) resolved so far, bounded by the number of airports. A missing or invalid
     * one is warned about when first resolved, instead of on every search from that airport.
     */
    private static final Map<String, ZoneId> ZONES = new ConcurrentHashMap<>();

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
        return ZONES.computeIfAbsent(zoneId == null ? "" : zoneId, DepartureDay::resolve);
    }

    private static ZoneId resolve(String zoneId) {
        if (zoneId.isBlank()) {
            log.warn("Origin airport has no time zone; searching its dates as UTC days");
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            log.warn("Origin airport time zone '{}' is not valid; searching its dates as UTC days", zoneId);
            return ZoneOffset.UTC;
        }
    }
}
