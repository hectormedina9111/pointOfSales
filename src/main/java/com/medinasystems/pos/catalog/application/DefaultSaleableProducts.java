package com.medinasystems.pos.catalog.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.catalog.infrastructure.ProductRepository;

/** Answers {@link SaleableProducts} from the product table. */
@Component
public class DefaultSaleableProducts implements SaleableProducts {

    private final ProductRepository products;

    public DefaultSaleableProducts(ProductRepository products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SaleableProduct> find(long productId) {
        return products.findById(productId).map(product -> new SaleableProduct(product.getId(), product.getSku(),
                product.getName(), product.isActive(), product.isRequiresExclusiveInvoice(), product.getCost(),
                product.getProductType().name()));
    }
}
