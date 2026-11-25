package com.medinasystems.pos.sales.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of a receipt counter: the cash register and the kind of document it numbers. */
public final class ReceiptCounterKey implements Serializable {

    private Long cashRegisterId;
    private String documentType;

    protected ReceiptCounterKey() {
    }

    public ReceiptCounterKey(Long cashRegisterId, String documentType) {
        this.cashRegisterId = cashRegisterId;
        this.documentType = documentType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ReceiptCounterKey key
                && Objects.equals(cashRegisterId, key.cashRegisterId)
                && Objects.equals(documentType, key.documentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cashRegisterId, documentType);
    }
}
