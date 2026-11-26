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
}
