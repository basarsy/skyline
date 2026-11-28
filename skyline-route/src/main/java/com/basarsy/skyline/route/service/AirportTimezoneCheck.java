package com.basarsy.skyline.route.service;

import com.basarsy.skyline.route.entity.Airport;
import com.basarsy.skyline.route.repository.AirportRepository;
import com.basarsy.skyline.route.validation.IanaTimeZone;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Names, at startup, every airport whose time zone is not an IANA region (stored before zones were validated):
 * its search days and local times are worked out in UTC until an admin corrects it with
 * {@code PATCH /api/v1/airports/{id}/timezone}. Logged with a stable marker to alert on; the right zone can't be
 * guessed from a typo, so nothing is changed here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirportTimezoneCheck implements ApplicationRunner {

    private final AirportRepository airports;

    @Override
    public void run(ApplicationArguments args) {
        invalid().forEach(airport -> log.error(
                "AIRPORT_TIMEZONE_INVALID airport={} iata={} timezone='{}': read as UTC until corrected with"
                        + " PATCH /api/v1/airports/{}/timezone", airport.getId(), airport.getIataCode(),
                airport.getTimezone(), airport.getId()));
    }

    List<Airport> invalid() {
        return airports.findAll().stream()
                .filter(airport -> airport.getTimezone() == null
                        || !IanaTimeZone.Validator.isRegion(airport.getTimezone()))
                .toList();
    }
}
