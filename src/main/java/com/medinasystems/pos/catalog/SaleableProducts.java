package com.medinasystems.pos.catalog;

import java.util.Optional;

/**
 * Public API of the catalog module for the sales and inventory modules. It answers what they copy from a product, without
 * exposing the {@code Product} entity. Products are never deleted, so an inactive product is still found and the
 * caller decides what to do with it.
 */
public interface SaleableProducts {

    /** The product with what a sale needs from it. Empty when the product does not exist. */
    Optional<SaleableProduct> find(long productId);
}
