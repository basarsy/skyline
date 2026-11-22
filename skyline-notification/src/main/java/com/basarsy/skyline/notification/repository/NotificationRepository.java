package com.basarsy.skyline.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code notifications}: each user's in-app inbox. */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    /** Adds an entry unless one with the same {@code dedupeKey} exists; true if it was added. */
    public boolean insert(UUID userId, String kind, String title, String body, String link, String dedupeKey) {
        return jdbc.update("""
                INSERT INTO notifications (id, user_id, kind, title, body, link, dedupe_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) DO NOTHING""",
                UUID.randomUUID(), userId, kind, title, body, link, dedupeKey) == 1;
    }

    /** The user's entries, newest first. */
    public List<StoredNotification> findByUser(UUID userId, int limit, long offset) {
        return jdbc.query("""
                SELECT id, kind, title, body, link, created_at, read_at FROM notifications
                WHERE user_id = ? ORDER BY created_at DESC, id LIMIT ? OFFSET ?""",
                NotificationRepository::map, userId, limit, offset);
    }

    public long countByUser(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Long.class, userId);
    }

    public long countUnread(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
    }

    /** Marks one of the user's entries read; false if the user has no such entry. */
    public boolean markRead(UUID userId, UUID id) {
        if (jdbc.update("UPDATE notifications SET read_at = now() WHERE id = ? AND user_id = ? AND read_at IS NULL",
                id, userId) == 1) {
            return true;
        }
        Long existing = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE id = ? AND user_id = ?",
                Long.class, id, userId);
        return existing != null && existing > 0;
    }

    public int markAllRead(UUID userId) {
        return jdbc.update("UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId);
    }

    private static StoredNotification map(ResultSet rs, int row) throws SQLException {
        return new StoredNotification(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("link"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("read_at", OffsetDateTime.class));
    }

    public record StoredNotification(UUID id, String kind, String title, String body, String link,
            OffsetDateTime createdAt, OffsetDateTime readAt) {}
}
