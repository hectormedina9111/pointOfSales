package com.medinasystems.pos.inventory.domain;

import com.medinasystems.pos.inventory.StockMovementType;

/**
 * Kinds of inventory document. Each one creates one movement per line: an entry adds the quantity, an exit removes it and
 * an adjustment moves the stock to the counted quantity. The prefix starts the document number.
 */
public enum InventoryDocumentType {
    ENTRY("IN", StockMovementType.ENTRY),
    EXIT("OUT", StockMovementType.EXIT),
    ADJUSTMENT("ADJ", StockMovementType.ADJUSTMENT);

    private final String prefix;
    private final StockMovementType movementType;

    InventoryDocumentType(String prefix, StockMovementType movementType) {
        this.prefix = prefix;
        this.movementType = movementType;
    }

    public String prefix() {
        return prefix;
    }

    public StockMovementType movementType() {
        return movementType;
    }

    /** Exits and adjustments must say why; an entry may leave the reason out. */
    public boolean requiresReason() {
        return this != ENTRY;
    }
}
