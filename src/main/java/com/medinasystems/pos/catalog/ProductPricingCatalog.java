package com.medinasystems.pos.catalog;

import java.util.Optional;

/**
 * Public API of the catalog module for pricing. It answers what is needed to price a product
 * in a country, without exposing the {@code Product} entity.
 */
public interface ProductPricingCatalog {

    /**
     * The product with its base price and the tax of the country: the country's row in
     * {@code product_tax} when it exists, otherwise the main tax of the product.
     * Empty when the product does not exist.
     */
    Optional<ProductPricingInfo> find(long productId, long countryId);
}
