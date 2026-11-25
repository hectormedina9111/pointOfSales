package com.medinasystems.pos.sync.application;

import java.time.Instant;

import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.domain.StoreNodeStatus;

/**
 * A registered store as the central node shows it. Secrets are never included.
 *
 * <p>{@code latestVersion}, {@code pendingChanges} and {@code connected} are computed when the view is built and
 * are not stored. {@code pendingUploadCount} and {@code oldestPendingAt} are what the store reported in its last
 * heartbeat: uploads it still has to send (for example, password changes made at the store).
 * {@code syncState} is DISABLED when the store was revoked, PENDING when it was never activated,
 * OUT_OF_SYNC when its last heartbeat is older than two minutes, and ACTIVE otherwise.
 * {@code returnConflicts} counts the returns of this store that exceeded the quantity left on their line.
 */
public record StoreNodeView(Long id, int nodeNumber, Long branchId, long idRangeStart, long idRangeEnd,
                            StoreNodeStatus status, Instant activatedAt, Instant lastHeartbeatAt,
                            Long lastDownloadVersion, String appVersion, Instant credentialRevokedAt,
                            long latestVersion, long pendingChanges, int pendingUploadCount,
                            Instant oldestPendingAt, boolean connected, StoreNodeStatus syncState,
                            long returnConflicts) {

    public static StoreNodeView from(StoreNode node, long latestVersion, Instant now, long returnConflicts) {
        boolean connected = node.isConnected(now);
        return new StoreNodeView(node.getId(), node.getNodeNumber(), node.getBranchId(), node.getIdRangeStart(),
                node.getIdRangeEnd(), node.getStatus(), node.getActivatedAt(), node.getLastHeartbeatAt(),
                node.getLastDownloadVersion(), node.getAppVersion(), node.getCredentialRevokedAt(), latestVersion,
                node.pendingChanges(latestVersion), node.getPendingUploadCount(), node.getOldestPendingAt(),
                connected, syncStateOf(node, connected), returnConflicts);
    }

    private static StoreNodeStatus syncStateOf(StoreNode node, boolean connected) {
        if (node.isRevoked()) {
            return StoreNodeStatus.DISABLED;
        }
        if (node.getStatus() == StoreNodeStatus.PENDING) {
            return StoreNodeStatus.PENDING;
        }
        return connected ? StoreNodeStatus.ACTIVE : StoreNodeStatus.OUT_OF_SYNC;
    }
}
