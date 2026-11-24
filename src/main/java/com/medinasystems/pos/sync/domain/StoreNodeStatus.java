package com.medinasystems.pos.sync.domain;

/** Life cycle of a store node as the central node sees it. */
public enum StoreNodeStatus {
    /** Registered, never activated: it still needs its activation code. */
    PENDING,
    /** Activated: its credential is valid. */
    ACTIVE,
    /** Reserved for the heartbeat (a later step): the store has not synchronized for a while. */
    OUT_OF_SYNC,
    /** Revoked by the central node: its credential no longer works. */
    DISABLED
}
