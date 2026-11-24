package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.medinasystems.pos.sync.application.SyncProtocol.ChangesPage;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotPage;
import com.medinasystems.pos.sync.domain.StoreIdentity;
import com.medinasystems.pos.sync.domain.SyncTable;
import com.medinasystems.pos.sync.domain.SyncTables;
import com.medinasystems.pos.sync.infrastructure.CentralSyncClient;
import com.medinasystems.pos.sync.infrastructure.ReplicaWriter;
import com.medinasystems.pos.sync.infrastructure.StoreIdentityRepository;

/**
 * Store side: the sync cycle. Every 30 seconds the store asks the central node for what changed and applies it.
 * The first time, it loads a full snapshot of the master data. Nothing here runs in a request, so the store keeps
 * answering reads when the central node is down.
 *
 * <p>Cycle: snapshot (only until it is complete), heartbeat, then changes page by page. Each page is applied in
 * one transaction together with the applied version, and then acknowledged to the central node. A failed cycle is
 * retried on the next one; applying a page twice is harmless because every write is an upsert or a replace.
 */
@Service
@Profile("store")
public class StoreReplicator {

    private static final Logger log = LoggerFactory.getLogger(StoreReplicator.class);
    static final int SNAPSHOT_PAGE = 1000;
    static final int CHANGES_PAGE = 500;
    private static final int MAX_ERROR_LENGTH = 500;

    private final StoreIdentityRepository identities;
    private final CentralSyncClient client;
    private final ReplicaWriter writer;
    private final StoreSyncState state;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Clock clock;

    public StoreReplicator(StoreIdentityRepository identities, CentralSyncClient client, ReplicaWriter writer,
                           StoreSyncState state, ObjectProvider<BuildProperties> buildProperties, Clock clock) {
        this.identities = identities;
        this.client = client;
        this.writer = writer;
        this.state = state;
        this.buildProperties = buildProperties;
        this.clock = clock;
    }

    /** One sync cycle. Errors are recorded in the identity row and logged without the token; the next cycle retries. */
    @Scheduled(fixedDelayString = "${pos.sync.interval:30s}", initialDelayString = "${pos.sync.initial-delay:15s}")
    public void runCycle() {
        Optional<StoreIdentity> identity = identities.findFirstByOrderByIdAsc();
        if (identity.isEmpty()) {
            return;
        }
        String token = identity.get().getStoreToken();
        try {
            if (identity.get().getSnapshotCompletedAt() == null) {
                loadSnapshot(token);
            }
            heartbeat(token);
            pullChanges(token);
            state.recordSuccess(clock.instant());
        } catch (SnapshotRequiredException ex) {
            // The changes this store needs were purged in the central node: the next cycle starts with a snapshot.
            state.requireSnapshot();
            state.recordError(clock.instant(), "The central node requires a new snapshot");
            log.warn("The central node purged changes this store still needs; a new snapshot will be loaded");
        } catch (RuntimeException ex) {
            String description = describe(ex);
            state.recordError(clock.instant(), description);
            log.warn("Sync with the central node failed: {}", description);
        }
    }

    /** Empties the replicated tables and loads every table page by page, then marks the snapshot complete. */
    private void loadSnapshot(String token) {
        long version = client.snapshotVersion(token).version();
        writer.clearReplicatedTables();
        for (SyncTable table : SyncTables.all()) {
            long afterId = 0L;
            boolean more = true;
            while (more) {
                SnapshotPage page = client.snapshot(token, table.name(), afterId, SNAPSHOT_PAGE);
                writer.applySnapshotPage(table, page.rows());
                afterId = page.nextAfterId();
                more = page.hasMore();
            }
        }
        state.completeSnapshot(version, clock.instant());
        log.info("Initial snapshot loaded; changes after version {} will follow", version);
    }

    private void heartbeat(String token) {
        HeartbeatResponse response = client.heartbeat(token,
                new HeartbeatRequest(appliedVersion(), appVersion(), 0, null));
        if (state.updateBranch(response.branchId())) {
            log.info("Store branch changed to {}", response.branchId());
        }
    }

    private void pullChanges(String token) {
        long since = appliedVersion();
        boolean more = true;
        while (more) {
            ChangesPage page = client.changes(token, since, CHANGES_PAGE);
            writer.applyChanges(page.changes(), page.lastVersion());
            client.ack(token, page.lastVersion());
            since = page.lastVersion();
            more = page.hasMore();
        }
    }

    private long appliedVersion() {
        return identities.findFirstByOrderByIdAsc().map(StoreIdentity::getLastAppliedVersion).orElse(0L);
    }

    private String appVersion() {
        BuildProperties build = buildProperties.getIfAvailable();
        return build != null ? build.getVersion() : "dev";
    }

    /** The class and message only: never the request, so never the token. */
    private static String describe(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String text = ex.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
        return text.substring(0, Math.min(text.length(), MAX_ERROR_LENGTH));
    }
}
