package com.medinasystems.pos.sync.application;

import java.util.LinkedHashMap;
import java.util.Map;

import com.medinasystems.pos.sync.domain.StoreNode;

/** The fields of a store node that the audit log keeps. Credentials are never part of it. */
final class StoreNodeSnapshot {

    private StoreNodeSnapshot() {
    }

    static Map<String, Object> of(StoreNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("nodeNumber", node.getNodeNumber());
        values.put("branchId", node.getBranchId());
        values.put("status", node.getStatus().name());
        values.put("idRangeStart", node.getIdRangeStart());
        values.put("idRangeEnd", node.getIdRangeEnd());
        values.put("credentialRevokedAt", node.getCredentialRevokedAt() == null ? null
                : node.getCredentialRevokedAt().toString());
        return values;
    }
}
