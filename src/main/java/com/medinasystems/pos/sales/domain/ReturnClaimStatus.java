package com.medinasystems.pos.sales.domain;

/**
 * State of a return claim. RESERVED: the central node holds the quantity for a return that is not uploaded yet.
 * CONFIRMED: the return reached the central node. RELEASED: the return failed and the quantity is free again.
 */
public enum ReturnClaimStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}
