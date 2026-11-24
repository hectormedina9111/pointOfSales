package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.application.SyncProtocol.Change;
import com.medinasystems.pos.sync.application.SyncProtocol.ChangesPage;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotPage;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotVersion;
import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.domain.SyncTable;
import com.medinasystems.pos.sync.domain.SyncTables;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;
import com.medinasystems.pos.sync.infrastructure.SyncDataReader;
import com.medinasystems.pos.sync.infrastructure.SyncDataReader.ChangedEntity;
import com.medinasystems.pos.sync.infrastructure.SyncDataReader.IdRow;

/**
 * Central node: serves the master data and the change log to the stores, and records what each store reports
 * (acknowledged versions and heartbeats). The stores always call; this service never calls them.
 */
@Service
@Profile("central")
public class CentralSyncService {

    /** Largest page a store can ask for. */
    public static final int MAX_PAGE = 2000;

    private final SyncDataReader reader;
    private final StoreNodeRepository nodes;
    private final Clock clock;

    public CentralSyncService(SyncDataReader reader, StoreNodeRepository nodes, Clock clock) {
        this.reader = reader;
        this.nodes = nodes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SnapshotVersion snapshotVersion() {
        return new SnapshotVersion(reader.latestVersion());
    }

    /**
     * One page of a replicated table.
     *
     * @throws BusinessRuleException UNKNOWN_SYNC_TABLE when the table is not replicated
     */
    @Transactional(readOnly = true)
    public SnapshotPage snapshot(String tableName, long afterId, int limit) {
        SyncTable table = SyncTables.find(tableName)
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_SYNC_TABLE", "The table is not replicated"));
        int size = pageSize(limit);
        long after = Math.max(0L, afterId);
        if (table.kind() == SyncTable.Kind.MAIN) {
            List<IdRow> rows = reader.mainPage(table, after, size);
            long next = rows.isEmpty() ? after : rows.getLast().id();
            return new SnapshotPage(table.name(), rows.stream().map(IdRow::rowJson).toList(), next,
                    rows.size() == size);
        }
        List<Long> parents = reader.parentIdPage(table, after, size);
        List<String> rows = parents.isEmpty() ? List.of() : reader.childRowsForParents(table, parents);
        long next = parents.isEmpty() ? after : parents.getLast();
        return new SnapshotPage(table.name(), rows, next, parents.size() == size);
    }

    /** Entities changed after {@code sinceVersion}, each with its current state. */
    @Transactional(readOnly = true)
    public ChangesPage changes(long sinceVersion, int limit) {
        int size = pageSize(limit);
        long since = Math.max(0L, sinceVersion);
        List<ChangedEntity> changed = reader.changedEntities(since, size);
        List<Change> changes = new ArrayList<>(changed.size());
        for (ChangedEntity entity : changed) {
            changes.add(toChange(entity));
        }
        long last = changed.isEmpty() ? since : changed.getLast().version();
        return new ChangesPage(changes, last, changed.size() == size);
    }

    /** Records that the store applied changes up to {@code version}. Only moves forward. */
    @Transactional
    public void acknowledge(long storeNodeId, long version) {
        find(storeNodeId).acknowledgeDownload(Math.max(0L, version));
    }

    /** Records a heartbeat and answers with the central node's latest version, so the store knows it is behind. */
    @Transactional
    public HeartbeatResponse heartbeat(long storeNodeId, HeartbeatRequest request) {
        StoreNode node = find(storeNodeId);
        node.recordHeartbeat(clock.instant(), Math.max(0L, request.appliedVersion()), request.appVersion(),
                request.pendingUploadCount(), request.oldestPendingAt());
        return new HeartbeatResponse(reader.latestVersion());
    }

    private Change toChange(ChangedEntity entity) {
        SyncTable table = SyncTables.mainOf(entity.entity())
                .orElseThrow(() -> new IllegalStateException("Change log entity is not replicated: " + entity.entity()));
        if ("UPSERT".equals(entity.operation())) {
            Optional<String> row = reader.rowJson(table, entity.entityId());
            if (row.isPresent()) {
                return new Change(entity.version(), entity.entity(), entity.entityId(), "UPSERT", row.get(),
                        reader.childRows(entity.entity(), entity.entityId()));
            }
        }
        // The row was deleted after its last change, or the last change is a DELETE.
        return new Change(entity.version(), entity.entity(), entity.entityId(), "DELETE", null, null);
    }

    private StoreNode find(long storeNodeId) {
        return nodes.findById(storeNodeId).orElseThrow(() -> new ResourceNotFoundException("store node", storeNodeId));
    }

    private static int pageSize(int limit) {
        return Math.min(MAX_PAGE, Math.max(1, limit));
    }
}
