package com.medinasystems.pos.catalog;

import java.math.BigDecimal;

/**
 * What a sale or an inventory movement needs about a product. {@code productType} is the name of the catalog type
 * ("GOOD" or "SERVICE"); {@code serialMode} is the name of its serial mode ("NONE", "MANUAL" or "LIST"); {@code cost}
 * may be null when the product has no cost. {@code categoryId} is null for a product
 * without category, and {@code minStock} is zero when none is set. {@code allowsDecimals} is the rule of its unit of
 * measure: false for pieces, true for weights and volumes.
 */
public record SaleableProduct(long id, String sku, String name, boolean active, boolean requiresExclusiveInvoice,
                              BigDecimal cost, String productType, boolean tracksInventory, String serialMode,
                              Long categoryId, BigDecimal minStock, boolean allowsDecimals) {

    /** True for a product whose units are equipment loaded with their serial numbers (serial mode LIST). */
    public boolean isSerialList() {
        return "LIST".equals(serialMode);
    }

    /** True for a product whose serial numbers are typed at the sale and do not identify equipment (serial mode MANUAL). */
    public boolean isSerialManual() {
        return "MANUAL".equals(serialMode);
    }

    /** True for a product whose sold units need a serial number: serial mode MANUAL or LIST. */
    public boolean hasSerials() {
        return serialMode != null && !"NONE".equals(serialMode);
    }
}
