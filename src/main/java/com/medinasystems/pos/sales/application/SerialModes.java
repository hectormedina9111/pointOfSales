package com.medinasystems.pos.sales.application;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;

/** The serial mode of a product (NONE, MANUAL or LIST), as the sales module reads it from the catalog. */
@Component
public class SerialModes {

    static final String NONE = "NONE";

    private final SaleableProducts products;

    public SerialModes(SaleableProducts products) {
        this.products = products;
    }

    /** The serial mode of the product, or NONE when the product does not exist. */
    public String of(long productId) {
        return products.find(productId).map(SaleableProduct::serialMode).orElse(NONE);
    }
}
