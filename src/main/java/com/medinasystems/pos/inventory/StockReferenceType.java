package com.medinasystems.pos.inventory;

/** What a movement refers to. {@link #INVENTORY_DOCUMENT} points to an inventory document of this module. */
public enum StockReferenceType {
    INVENTORY_DOCUMENT,
    SALE,
    SALE_RETURN,
    TRANSFER
}
