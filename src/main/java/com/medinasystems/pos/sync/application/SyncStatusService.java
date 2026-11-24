package com.medinasystems.pos.sync.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.infrastructure.StoreIdentityRepository;

/** Reports the sync status of this node. The store token is never part of it. */
@Service
@Transactional(readOnly = true)
public class SyncStatusService {

    private final NodeProperties node;
    private final StoreIdentityRepository identities;

    public SyncStatusService(NodeProperties node, StoreIdentityRepository identities) {
        this.node = node;
        this.identities = identities;
    }

    public SyncStatusView status() {
        if (node.isCentral()) {
            return new SyncStatusView("CENTRAL", false, null, null, null, null, null, null, null, null, null, null);
        }
        return identities.findFirstByOrderByIdAsc()
                .map(identity -> new SyncStatusView("STORE", true, identity.getNodeNumber(),
                        identity.getStoreNodeId(), identity.getBranchId(), identity.getIdRangeStart(),
                        identity.getIdRangeEnd(), identity.getLastAppliedVersion(),
                        identity.getSnapshotCompletedAt(), identity.getCentralUrl(), identity.getLastSyncAt(),
                        identity.getLastSyncError()))
                .orElseGet(() -> new SyncStatusView("STORE", false, node.number(), null, null, null, null, null,
                        null, node.centralUrl(), null, null));
    }
}
