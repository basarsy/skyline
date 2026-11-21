package com.basarsy.skyline.schedule.outbox;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC access to {@code outbox_events}. It joins the surrounding (JPA) transaction, so a row written here
 * commits or rolls back together with the entity change it describes.
 */
@Repository
@RequiredArgsConstructor
class OutboxRepository {

    private final JdbcTemplate jdbc;

    void insert(UUID aggregateId, String eventType, String topic, String payload) {
        jdbc.update("INSERT INTO outbox_events (id, aggregate_id, event_type, topic, payload) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), aggregateId, eventType, topic, payload);
    }

    /**
     * Locks up to {@code limit} due, unpublished rows, oldest first, until the transaction ends. SKIP LOCKED
     * leaves rows another schedule instance has already claimed to that instance instead of waiting for them,
     * so concurrent publishers never send the same row at the same time.
     */
    List<OutboxEvent> claimBatch(int limit) {
        return jdbc.query("""
                SELECT id, aggregate_id, topic, payload, attempts FROM outbox_events
                WHERE published_at IS NULL AND next_attempt_at <= now()
                ORDER BY next_attempt_at, created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED""",
                (rs, row) -> new OutboxEvent(rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                        rs.getString("topic"), rs.getString("payload"), rs.getInt("attempts")),
                limit);
    }

    void markPublished(Collection<UUID> ids) {
        jdbc.batchUpdate("UPDATE outbox_events SET published_at = now() WHERE id = ?",
                ids.stream().map(id -> new Object[] {id}).toList());
    }

    void markFailed(UUID id, String error, Duration backoff) {
        jdbc.update("""
                UPDATE outbox_events
                SET attempts = attempts + 1, last_error = ?, next_attempt_at = now() + ? * INTERVAL '1 millisecond'
                WHERE id = ?""",
                error, backoff.toMillis(), id);
    }

    int deletePublishedBefore(Duration retention) {
        return jdbc.update("DELETE FROM outbox_events WHERE published_at < now() - ? * INTERVAL '1 millisecond'",
                retention.toMillis());
    }
}
