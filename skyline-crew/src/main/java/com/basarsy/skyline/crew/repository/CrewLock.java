package com.basarsy.skyline.crew.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Serialises rostering of one crew member until the transaction ends, so two people assigning the same captain
 * to overlapping flights at the same moment can't both pass the overlap check.
 */
@Repository
@RequiredArgsConstructor
public class CrewLock {

    private final JdbcTemplate jdbc;

    public void lock(UUID crewMemberId) {
        // A 64-bit key from the UUID's own bits: a 32-bit hash would let unrelated crew members wait on each other.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null,
                crewMemberId.getMostSignificantBits() ^ crewMemberId.getLeastSignificantBits());
    }
}
