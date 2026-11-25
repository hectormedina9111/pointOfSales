package com.medinasystems.pos.pricing;

/**
 * Public API of the pricing module for other modules. Lets the customer module check that a price list exists on this
 * node without reading the pricing tables.
 */
public interface PriceListDirectory {

    /** True when a price list with that id is stored on this node. */
    boolean exists(long priceListId);
}
