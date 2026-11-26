package com.basarsy.skyline.common.outbox;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Plain JDBC access to {@code outbox_events}. It joins the surrounding (JPA) transaction, so a row written here
 * commits or rolls back together with the entity change it describes.
 *
 * <p>Times: {@code now()} is the start of the current transaction, {@code clock_timestamp()} the actual time.
 * Comparisons use {@code now()} (stable, so the partial indexes serve them), which is accurate because the
 * claim and each cleanup chunk run first in short transactions of their own. Times recorded after a send
 * ({@code published_at}, {@code next_attempt_at}) use {@code clock_timestamp()}: the publisher's transaction
 * spans the sends, so {@code now()} there would be the claim time, seconds before the ack or failure.
 */
@RequiredArgsConstructor
class OutboxRepository {

    private final JdbcTemplate jdbc;

    void insert(UUID aggregateId, String eventType, String topic, String payload) {
        insert(aggregateId, eventType, topic, payload, null);
    }

    void insert(UUID aggregateId, String eventType, String topic, String payload, String traceParent) {
        jdbc.update("""
                INSERT INTO outbox_events (id, aggregate_id, event_type, topic, payload, trace_parent)
                VALUES (?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), aggregateId, eventType, topic, payload, traceParent);
    }

    /**
     * Locks up to {@code limit} due, unpublished rows, oldest first, until the transaction ends. SKIP LOCKED
     * leaves rows another schedule instance has already claimed to that instance instead of waiting for them,
     * so concurrent publishers never send the same row at the same time.
     *
     * <p>Only the oldest unpublished row of each aggregate can be claimed: a later one waits until it is
     * published, even while it backs off after a failure, so events reach Kafka in order per key. A batch
     * therefore holds at most one row per aggregate.
     */
    List<OutboxEvent> claimBatch(int limit) {
        return jdbc.query("""
                SELECT e.id, e.aggregate_id, e.topic, e.payload, e.attempts, e.trace_parent FROM outbox_events e
                WHERE e.published_at IS NULL AND e.next_attempt_at <= now()
                  AND NOT EXISTS (
                      SELECT 1 FROM outbox_events older
                      WHERE older.aggregate_id = e.aggregate_id AND older.published_at IS NULL AND older.seq < e.seq)
                ORDER BY e.next_attempt_at, e.seq
                LIMIT ?
                FOR UPDATE OF e SKIP LOCKED""",
                (rs, row) -> new OutboxEvent(rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                        rs.getString("topic"), rs.getString("payload"), rs.getInt("attempts"),
                        rs.getString("trace_parent")),
                limit);
    }

    void markPublished(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement statement =
                    con.prepareStatement("UPDATE outbox_events SET published_at = clock_timestamp() WHERE id = ANY(?)");
            statement.setArray(1, con.createArrayOf("uuid", ids.toArray()));
            return statement;
        });
    }

    /** Records a failure; the row is due again {@code backoff} after now (the failure), not after the claim. */
    void markFailed(UUID id, int attempts, String error, Duration backoff) {
        jdbc.update("""
                UPDATE outbox_events
                SET attempts = ?, last_error = ?, next_attempt_at = clock_timestamp() + ? * INTERVAL '1 millisecond'
                WHERE id = ?""",
                attempts, error, backoff.toMillis(), id);
    }

    /** Deletes up to {@code limit} rows published more than {@code retention} ago and returns how many. */
    int deletePublishedBefore(Duration retention, int limit) {
        return jdbc.update("""
                DELETE FROM outbox_events WHERE id IN (
                    SELECT id FROM outbox_events
                    WHERE published_at < now() - ? * INTERVAL '1 millisecond'
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)""",
                retention.toMillis(), limit);
    }
}
