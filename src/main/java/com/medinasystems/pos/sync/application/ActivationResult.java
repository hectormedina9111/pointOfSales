package com.medinasystems.pos.sync.application;

/**
 * What a store receives when it activates: its identity and its permanent token. The token is secret: it is
 * never logged and is returned only to the activation call.
 */
public record ActivationResult(long storeNodeId, int nodeNumber, Long branchId, long idRangeStart, long idRangeEnd,
                               String storeToken) {
}
