package com.medinasystems.pos.catalog;

import java.math.BigDecimal;

/**
 * What a sale needs about a product. {@code productType} is the name of the catalog type ("GOOD" or "SERVICE");
 * {@code cost} may be null when the product has no cost.
 */
public record SaleableProduct(long id, String sku, String name, boolean active, boolean requiresExclusiveInvoice,
                              BigDecimal cost, String productType) {
}
