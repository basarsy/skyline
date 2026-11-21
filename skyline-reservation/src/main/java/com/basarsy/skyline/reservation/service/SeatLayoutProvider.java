package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.FleetClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The seat layout of a flight's aircraft type, from the fleet service, kept in memory for a few minutes: every seat
 * map, hold, price and check-in assignment reads it, and layouts change rarely (an admin edit shows up within the
 * TTL). A type without a layout is cached as "none" too, so its flights fall back to unassigned-until-check-in.
 */
@Slf4j
@Component
public class SeatLayoutProvider {

    private record Entry(Optional<SeatLayout> layout, Instant fetchedAt) {}

    private final FleetClient fleetClient;
    private final Duration ttl;
    private final Clock clock;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public SeatLayoutProvider(FleetClient fleetClient,
            @Value("${skyline.seats.layout-cache-ttl:PT5M}") Duration ttl, Clock clock) {
        this.fleetClient = fleetClient;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** The layout, or empty when the aircraft type has none. Throws 503 when the flight has no aircraft details. */
    public Optional<SeatLayout> forFlight(FlightResponse flight) {
        if (flight == null || flight.getAircraft() == null || flight.getAircraft().aircraftType() == null) {
            throw new SkylineException("Aircraft details unavailable, please retry", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return forType(flight.getAircraft().aircraftType().id());
    }

    /**
     * Like {@link #forFlight}, but never fails: no aircraft details, or the fleet service unreachable, is "no layout".
     * For checks that must not make booking depend on the fleet service (booking worked without it before).
     */
    public Optional<SeatLayout> tryForFlight(FlightResponse flight) {
        try {
            return forFlight(flight);
        } catch (RuntimeException e) {
            log.warn("Seat layout unavailable for flight {} ({}); skipping layout checks",
                    flight == null ? null : flight.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Like {@link #forFlight}, but a missing layout is a 404 with a message the passenger can act on. */
    public SeatLayout requireForFlight(FlightResponse flight) {
        return forFlight(flight).orElseThrow(() -> new SkylineException(
                "Seat selection is not available on this aircraft; a seat is assigned at check-in",
                HttpStatus.NOT_FOUND));
    }

    Optional<SeatLayout> forType(UUID aircraftTypeId) {
        Instant now = clock.instant();
        Entry cached = cache.get(aircraftTypeId);
        if (cached != null && cached.fetchedAt().plus(ttl).isAfter(now)) {
            return cached.layout();
        }
        Optional<SeatLayout> layout;
        try {
            layout = Optional.of(fleetClient.getSeatLayout(aircraftTypeId));
        } catch (SkylineException e) {
            if (e.getStatus() != HttpStatus.NOT_FOUND) {
                throw e;
            }
            layout = Optional.empty();
        }
        cache.put(aircraftTypeId, new Entry(layout, now));
        return layout;
    }
}
