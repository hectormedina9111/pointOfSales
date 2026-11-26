package com.medinasystems.pos.catalog.application;

import java.math.BigDecimal;

import com.medinasystems.pos.catalog.domain.ProductType;
import com.medinasystems.pos.catalog.domain.SerialMode;

/** Input for creating or changing a product. Defaults for omitted flags are applied by the web layer. */
public record ProductData(String sku, String barcode, String name, String description, Long categoryId,
                          Long taxId, Long unitOfMeasureId, BigDecimal salePrice, BigDecimal cost,
                          ProductType productType, boolean tracksInventory, SerialMode serialMode,
                          boolean requiresExclusiveInvoice, BigDecimal minStock, String imageUrl, boolean active) {
}
