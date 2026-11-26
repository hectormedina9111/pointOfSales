package com.medinasystems.pos.catalog.domain;

/**
 * How a product handles serial numbers. NONE: stock by quantity only. MANUAL: stock by quantity, and each sold unit
 * takes a serial typed or scanned at the sale. LIST: each unit is an equipment loaded before, with its own data.
 */
public enum SerialMode {
    NONE,
    MANUAL,
    LIST;

    /** True for MANUAL and LIST: the product needs one serial number per unit in sales and returns. */
    public boolean hasSerials() {
        return this != NONE;
    }
}
