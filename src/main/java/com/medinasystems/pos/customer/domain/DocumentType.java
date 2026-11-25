package com.medinasystems.pos.customer.domain;

/** Identity document of the customer. NONE is the final consumer, who gives no document. */
public enum DocumentType {
    RTN,
    DNI,
    PASSPORT,
    NONE
}
