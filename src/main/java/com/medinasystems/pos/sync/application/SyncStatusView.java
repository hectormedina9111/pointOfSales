package com.medinasystems.pos.sync.application;

import java.time.Instant;

/**
 * Sync status of this node. On the central node, {@code node} is CENTRAL and the other fields are null.
 * On a store, {@code node} is STORE and {@code activated} tells whether the store has its identity yet.
 */
public record SyncStatusView(String node, boolean activated, Integer nodeNumber, Long storeNodeId, Long branchId,
                             Long idRangeStart, Long idRangeEnd, Long lastAppliedVersion,
                             Instant snapshotCompletedAt, String centralUrl) {
}
