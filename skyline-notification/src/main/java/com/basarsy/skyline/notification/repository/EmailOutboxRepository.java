package com.basarsy.skyline.notification.repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code email_outbox}: emails waiting to be sent (see EmailSender). */
@Repository
@RequiredArgsConstructor
public class EmailOutboxRepository {

    private final JdbcTemplate jdbc;

    /** Queues an email unless one with the same {@code dedupeKey} was queued before; true if it was queued. */
    public boolean enqueue(String recipient, String subject, String body, String dedupeKey) {
        return jdbc.update("""
                INSERT INTO email_outbox (id, recipient, subject, body, dedupe_key) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) DO NOTHING""",
                UUID.randomUUID(), recipient, subject, body, dedupeKey) == 1;
    }

    /**
     * Locks up to {@code limit} due, unsent emails, oldest first, until the transaction ends. SKIP LOCKED leaves
     * emails another instance has claimed to it, so no email is sent twice at the same time.
     */
    public List<PendingEmail> claimDue(int limit) {
        return jdbc.query("""
                SELECT id, recipient, subject, body, attempts FROM email_outbox
                WHERE sent_at IS NULL AND next_attempt_at <= now()
                ORDER BY next_attempt_at, seq
                LIMIT ?
                FOR UPDATE SKIP LOCKED""",
                (rs, row) -> new PendingEmail(rs.getObject("id", UUID.class), rs.getString("recipient"),
                        rs.getString("subject"), rs.getString("body"), rs.getInt("attempts")),
                limit);
    }

    public void markSent(UUID id) {
        jdbc.update("UPDATE email_outbox SET sent_at = clock_timestamp() WHERE id = ?", id);
    }

    /** Records a failure; the email is due again {@code backoff} after now. */
    public void markFailed(UUID id, int attempts, String error, Duration backoff) {
        jdbc.update("""
                UPDATE email_outbox
                SET attempts = ?, last_error = ?, next_attempt_at = clock_timestamp() + ? * INTERVAL '1 millisecond'
                WHERE id = ?""",
                attempts, error, backoff.toMillis(), id);
    }

    public record PendingEmail(UUID id, String recipient, String subject, String body, int attempts) {}
}
