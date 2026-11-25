package com.basarsy.skyline.schedule.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Serialises scheduling of one aircraft until the transaction ends: checking that it is free and saving the
 * flight then happen as one step, so two people putting the same aircraft on overlapping flights at the same
 * moment can't both pass the check.
 */
@Repository
@RequiredArgsConstructor
public class AircraftLock {

    private final JdbcTemplate jdbc;

    public void lock(UUID aircraftId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, "aircraft:" + aircraftId);
    }
}
