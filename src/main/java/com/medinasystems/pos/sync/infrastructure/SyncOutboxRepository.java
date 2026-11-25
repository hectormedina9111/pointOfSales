package com.medinasystems.pos.sync.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Store side only: the queue of changes waiting to be uploaded to the central node (table sync_outbox). Rows are
 * appended and then marked as answered; they are never deleted. Plain JDBC because the payload is JSONB.
 */
@Component
@Profile("store")
public class SyncOutboxRepository {

    private final JdbcTemplate jdbc;

    public SyncOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds a pending row. The payload must be a JSON text; it is stored as JSONB. */
    public void append(String eventType, String payloadJson, Instant createdAt) {
        jdbc.update("INSERT INTO sync_outbox (event_type, payload, created_at) VALUES (?, ?::jsonb, ?)",
                eventType, payloadJson, Timestamp.from(createdAt));
    }

    /** The oldest pending rows, at most {@code limit}. */
    public List<PendingRow> findPending(int limit) {
        return jdbc.query("SELECT id, event_type, payload::text AS payload FROM sync_outbox"
                        + " WHERE uploaded_at IS NULL ORDER BY id LIMIT ?",
                (rs, rowNum) -> new PendingRow(rs.getLong("id"), rs.getString("event_type"), rs.getString("payload")),
                limit);
    }

    /** Marks a row answered by the central node. {@code code} is kept in last_error and may be null. */
    public void markUploaded(long id, String result, String code, Instant answeredAt) {
        jdbc.update("UPDATE sync_outbox SET uploaded_at = ?, result = ?, last_error = ?"
                        + " WHERE id = ? AND uploaded_at IS NULL",
                Timestamp.from(answeredAt), result, code, id);
    }

    /** Counts a failed upload attempt for each row and keeps the error class name. */
    public void recordFailedAttempt(List<Long> ids, String error) {
        for (Long id : ids) {
            jdbc.update("UPDATE sync_outbox SET attempts = attempts + 1, last_error = ?"
                    + " WHERE id = ? AND uploaded_at IS NULL", error, id);
        }
    }

    public int pendingCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM sync_outbox WHERE uploaded_at IS NULL", Long.class);
        return count == null ? 0 : count.intValue();
    }

    public Optional<Instant> oldestPendingAt() {
        Timestamp oldest = jdbc.queryForObject("SELECT min(created_at) FROM sync_outbox WHERE uploaded_at IS NULL",
                Timestamp.class);
        return Optional.ofNullable(oldest).map(Timestamp::toInstant);
    }

    /** A pending row: its id, type and JSON payload as text. */
    public record PendingRow(long id, String eventType, String payloadJson) {
    }
}
