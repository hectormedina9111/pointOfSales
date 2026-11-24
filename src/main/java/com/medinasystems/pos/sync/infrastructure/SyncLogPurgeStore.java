package com.medinasystems.pos.sync.infrastructure;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Central node only: the SQL behind {@code SyncLogPurger}. The retention period is 30 days, counted with the
 * database clock as in the rest of the change log.
 */
@Component
@Profile("central")
public class SyncLogPurgeStore {

    private static final String OLD_ROWS = "version <= ? AND changed_at < now() - interval '30 days'";

    private final JdbcTemplate jdbc;

    public SyncLogPurgeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lowest version that every active store has downloaded. A store counts as active when it is activated and not
     * revoked. A store that never reported a download counts as 0, so nothing is purged for it. Empty when there
     * are no active stores.
     */
    public Optional<Long> minActiveDownloadVersion() {
        Long version = jdbc.queryForObject("SELECT MIN(COALESCE(last_download_version, 0)) FROM store_node"
                + " WHERE activated_at IS NOT NULL AND credential_revoked_at IS NULL", Long.class);
        return Optional.ofNullable(version);
    }

    /**
     * Deletes the old rows up to {@code bound} and records the highest deleted version in {@code sync_purge_state}.
     *
     * @return number of rows deleted
     */
    public int purge(long bound) {
        Long highest = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM sync_change_log WHERE " + OLD_ROWS,
                Long.class, bound);
        int deleted = jdbc.update("DELETE FROM sync_change_log WHERE " + OLD_ROWS, bound);
        if (deleted > 0) {
            jdbc.update("UPDATE sync_purge_state SET purged_up_to = GREATEST(purged_up_to, ?) WHERE id = 1",
                    highest == null ? 0L : highest);
        }
        return deleted;
    }
}
