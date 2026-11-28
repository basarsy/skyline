package com.basarsy.skyline.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class DepartureDayTest {

    // Past dates throughout: future offsets change with the JDK's time zone data, past ones don't.
    private static final LocalDate JUNE_1 = LocalDate.of(2025, 6, 1);

    @Test
    void istanbulDayStartsAt21UtcTheEveningBefore() {
        DepartureDay day = DepartureDay.of(JUNE_1, "Europe/Istanbul");

        assertThat(day.start()).isEqualTo(LocalDateTime.of(2025, 5, 31, 21, 0));
        assertThat(day.end()).isEqualTo(LocalDateTime.of(2025, 6, 1, 21, 0));
    }

    @Test
    void aNightDepartureFromIstanbulBelongsToTheLocalDateNotTheUtcOne() {
        // 01:30 in Istanbul on 1 June is 22:30 UTC on 31 May.
        LocalDateTime departure = LocalDateTime.of(2025, 5, 31, 22, 30);

        assertThat(contains(DepartureDay.of(JUNE_1, "Europe/Istanbul"), departure)).isTrue();
        assertThat(contains(DepartureDay.of(JUNE_1.minusDays(1), "Europe/Istanbul"), departure)).isFalse();
    }

    @Test
    void springForwardDayIs23Hours() {
        // London moved to BST at 01:00 UTC on 30 March 2025.
        DepartureDay day = DepartureDay.of(LocalDate.of(2025, 3, 30), "Europe/London");

        assertThat(day.start()).isEqualTo(LocalDateTime.of(2025, 3, 30, 0, 0));
        assertThat(day.end()).isEqualTo(LocalDateTime.of(2025, 3, 30, 23, 0));
        assertThat(Duration.between(day.start(), day.end())).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void fallBackDayIs25Hours() {
        // New York left EDT at 06:00 UTC on 2 November 2025.
        DepartureDay day = DepartureDay.of(LocalDate.of(2025, 11, 2), "America/New_York");

        assertThat(day.start()).isEqualTo(LocalDateTime.of(2025, 11, 2, 4, 0));
        assertThat(day.end()).isEqualTo(LocalDateTime.of(2025, 11, 3, 5, 0));
        assertThat(Duration.between(day.start(), day.end())).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void aZoneWhoseMidnightFallsInTheDstGapStartsAtTheFirstValidInstant() {
        // Havana sprang forward at local midnight (00:00 → 01:00 CDT) on 12 March 2023: the day started at 01:00.
        // A past date on purpose: future rules change with the JDK's time zone data (Cuba's 2027 ones did).
        DepartureDay day = DepartureDay.of(LocalDate.of(2023, 3, 12), "America/Havana");

        assertThat(day.start()).isEqualTo(LocalDateTime.of(2023, 3, 12, 5, 0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Mars/Olympus_Mons", "not a zone"})
    void aMissingOrUnknownZoneFallsBackToTheUtcDay(String zone) {
        DepartureDay day = DepartureDay.of(JUNE_1, zone);

        assertThat(day.start()).isEqualTo(JUNE_1.atStartOfDay());
        assertThat(day.end()).isEqualTo(JUNE_1.plusDays(1).atStartOfDay());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void anInvalidZoneIsWarnedAboutOnceNotOnEverySearch(CapturedOutput output) {
        String zone = "Bad/Zone-" + UUID.randomUUID();
        String other = "Bad/Zone-" + UUID.randomUUID();

        DepartureDay.of(JUNE_1, zone);
        DepartureDay.of(JUNE_1.plusDays(1), zone);
        DepartureDay.of(JUNE_1, zone);
        DepartureDay.of(JUNE_1, other);

        assertThat(output.getOut().split(Pattern.quote(zone), -1)).as("one warning for the zone").hasSize(2);
        assertThat(output.getOut()).contains(other);
    }

    private static boolean contains(DepartureDay day, LocalDateTime departure) {
        return !departure.isBefore(day.start()) && departure.isBefore(day.end());
    }
}
