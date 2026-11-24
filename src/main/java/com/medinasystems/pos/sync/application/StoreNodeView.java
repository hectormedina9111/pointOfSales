package com.medinasystems.pos.sync.application;

import java.time.Instant;

import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.domain.StoreNodeStatus;

/** A registered store as the central node shows it. Secrets are never included. */
public record StoreNodeView(Long id, int nodeNumber, Long branchId, long idRangeStart, long idRangeEnd,
                            StoreNodeStatus status, Instant activatedAt, Instant lastHeartbeatAt,
                            Long lastDownloadVersion, String appVersion, Instant credentialRevokedAt) {

    public static StoreNodeView from(StoreNode node) {
        return new StoreNodeView(node.getId(), node.getNodeNumber(), node.getBranchId(), node.getIdRangeStart(),
                node.getIdRangeEnd(), node.getStatus(), node.getActivatedAt(), node.getLastHeartbeatAt(),
                node.getLastDownloadVersion(), node.getAppVersion(), node.getCredentialRevokedAt());
    }
}
