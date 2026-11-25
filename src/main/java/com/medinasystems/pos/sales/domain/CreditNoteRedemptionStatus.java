package com.medinasystems.pos.sales.domain;

/** Where a redemption of a credit note balance stands. */
public enum CreditNoteRedemptionStatus {
    /** The central node took the amount from the balance. */
    APPLIED,
    /** The sale that used the redemption reached the central node. */
    CONFIRMED,
    /** The payment failed and the amount went back to the balance. */
    RELEASED
}
