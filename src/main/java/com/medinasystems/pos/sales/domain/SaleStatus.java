package com.medinasystems.pos.sales.domain;

/** ON_HOLD: saved and not paid, it can be resumed or deleted. PAID: paid, it is a document and is never deleted. */
public enum SaleStatus {
    ON_HOLD,
    PAID
}
