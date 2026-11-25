package com.medinasystems.pos.sales.domain;

/**
 * ON_HOLD: saved and not paid, it can be resumed or deleted. PAID: paid, it is a document and is never deleted.
 * PARTIALLY_RETURNED and RETURNED: a paid sale with some or all of its quantities returned (only for SALE documents).
 */
public enum SaleStatus {
    ON_HOLD,
    PAID,
    PARTIALLY_RETURNED,
    RETURNED
}
