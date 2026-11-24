package com.medinasystems.pos.catalog.domain;

import java.math.BigDecimal;

/**
 * The editable fields of a product, with the references already resolved. The entity validates
 * them all before changing anything.
 */
public record ProductDetails(String sku, String barcode, String name, String description,
                             Category category, Tax tax, UnitOfMeasure unitOfMeasure,
                             BigDecimal salePrice, BigDecimal cost, ProductType productType,
                             boolean tracksInventory, boolean tracksSerials, boolean requiresExclusiveInvoice,
                             BigDecimal minStock, String imageUrl, boolean active) {
}
