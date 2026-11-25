package com.medinasystems.pos.sync.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Messages between a store and the central node. Rows travel as JSON text (one string per row, as PostgreSQL
 * produces it with row_to_json), so the central node never maps them to Java types and the store hands them
 * straight to PostgreSQL.
 */
public final class SyncProtocol {

    private SyncProtocol() {
    }

    /** Current version of the change log on the central node. Zero when there are no changes yet. */
    public record SnapshotVersion(long version) {
    }

    /**
     * One page of a table. For MAIN tables {@code nextAfterId} is the last id of the page; for CHILD tables it is
     * the last parent id of the page. Pass it back as {@code afterId} to get the next page.
     */
    public record SnapshotPage(String table, List<String> rows, long nextAfterId, boolean hasMore) {
    }

    /**
     * One entity that changed: its current row (null for DELETE) and, for entities with child tables, the current
     * child rows of each child table, keyed by table name.
     */
    public record Change(long version, String entity, long entityId, String operation, String rowJson,
                         Map<String, List<String>> children) {
    }

    /** Changes after the requested version. {@code lastVersion} is the version to ask for next time. */
    public record ChangesPage(List<Change> changes, long lastVersion, boolean hasMore) {
    }

    public record AckRequest(@NotNull @PositiveOrZero Long version) {
    }

    /** What the store reports about itself. The central node answers with its own latest version. */
    public record HeartbeatRequest(@PositiveOrZero long appliedVersion, String appVersion,
                                   @Min(0) int pendingUploadCount, Instant oldestPendingAt) {
    }

    /**
     * The central node's latest version, and the branch it has for this store (null when none). The store updates
     * its own branch from it, so a branch assigned in the central node reaches the store on the next heartbeat.
     */
    public record HeartbeatResponse(long latestVersion, Long branchId) {
    }
}
