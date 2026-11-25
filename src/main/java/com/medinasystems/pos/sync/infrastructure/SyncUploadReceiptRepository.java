package com.medinasystems.pos.sync.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Central node only: one receipt per upload item, keyed by store and outbox id (table sync_upload_receipt). */
@Component
@Profile("central")
public class SyncUploadReceiptRepository {

    private final JdbcTemplate jdbc;

    public SyncUploadReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The stored result (ACCEPTED or REJECTED) of an item already received, or empty. */
    public Optional<String> findResult(long storeNodeId, long outboxId) {
        return jdbc.query("SELECT result FROM sync_upload_receipt WHERE store_node_id = ? AND outbox_id = ?",
                (rs, rowNum) -> rs.getString("result"), storeNodeId, outboxId).stream().findFirst();
    }

    public void save(long storeNodeId, long outboxId, Instant receivedAt, String result) {
        jdbc.update("INSERT INTO sync_upload_receipt (store_node_id, outbox_id, received_at, result)"
                        + " VALUES (?, ?, ?, ?)",
                storeNodeId, outboxId, Timestamp.from(receivedAt), result);
    }
}
