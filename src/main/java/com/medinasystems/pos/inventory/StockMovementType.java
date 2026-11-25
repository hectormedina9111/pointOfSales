package com.medinasystems.pos.inventory;

/**
 * Kinds of stock movement. The incoming ones add stock and the outgoing ones remove it; the sign of a movement's quantity
 * always follows its kind.
 */
public enum StockMovementType {
    ENTRY(true),
    EXIT(false),
    ADJUSTMENT(null),
    SALE_OUT(false),
    RETURN_IN(true),
    TRANSFER_OUT(false),
    TRANSFER_IN(true);

    private final Boolean incoming;

    StockMovementType(Boolean incoming) {
        this.incoming = incoming;
    }

    /** True when the kind always adds stock, false when it always removes it, null when the sign depends on the count. */
    public Boolean incoming() {
        return incoming;
    }
}
