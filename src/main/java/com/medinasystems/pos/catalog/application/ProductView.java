package com.medinasystems.pos.catalog.application;

import java.math.BigDecimal;

import com.medinasystems.pos.catalog.domain.Product;
import com.medinasystems.pos.catalog.domain.ProductType;
import com.medinasystems.pos.catalog.domain.SerialMode;

public record ProductView(Long id, String sku, String barcode, String name, String description, Long categoryId,
                          Long taxId, Long unitOfMeasureId, BigDecimal salePrice, boolean priceIncludesTax,
                          BigDecimal cost, ProductType productType, boolean tracksInventory, SerialMode serialMode,
                          boolean tracksSerials, boolean requiresExclusiveInvoice, BigDecimal minStock, String imageUrl,
                          boolean active) {

    /**
     * {@code tracksSerials} is derived from {@code serialMode} (true only for LIST) and is kept so the frontend can keep
     * reading it during the transition. Write requests only accept {@code serialMode}.
     */
    public static ProductView from(Product product) {
        return new ProductView(product.getId(), product.getSku(), product.getBarcode(), product.getName(),
                product.getDescription(), product.getCategory() == null ? null : product.getCategory().getId(),
                product.getTax().getId(), product.getUnitOfMeasure().getId(), product.getSalePrice(),
                product.isPriceIncludesTax(), product.getCost(), product.getProductType(),
                product.isTracksInventory(), product.getSerialMode(),
                product.getSerialMode() == SerialMode.LIST, product.isRequiresExclusiveInvoice(),
                product.getMinStock(), product.getImageUrl(), product.isActive());
    }
}
