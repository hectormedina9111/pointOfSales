package com.medinasystems.pos.inventory.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of a document counter: the branch and the kind of document it numbers. */
public final class InventoryDocumentCounterKey implements Serializable {

    private Long branchId;
    private String documentType;

    protected InventoryDocumentCounterKey() {
    }

    public InventoryDocumentCounterKey(Long branchId, String documentType) {
        this.branchId = branchId;
        this.documentType = documentType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryDocumentCounterKey key
                && Objects.equals(branchId, key.branchId)
                && Objects.equals(documentType, key.documentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(branchId, documentType);
    }
}
