package com.medinasystems.pos.inventory.domain;

/** Which side of a transfer a list shows, relative to the branch it is asked for. */
public enum TransferDirection {
    /** Transfers the branch sent: the branch is the origin. */
    OUTGOING,
    /** Transfers the branch receives: the branch is the destination. */
    INCOMING
}
