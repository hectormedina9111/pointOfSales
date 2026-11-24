package com.medinasystems.pos.catalog.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.ProductPricingCatalog;
import com.medinasystems.pos.catalog.ProductPricingInfo;
import com.medinasystems.pos.catalog.domain.ProductTax;
import com.medinasystems.pos.catalog.domain.Tax;
import com.medinasystems.pos.catalog.infrastructure.ProductRepository;
import com.medinasystems.pos.catalog.infrastructure.ProductTaxRepository;

/** Answers {@link ProductPricingCatalog} from the product and product_tax tables. */
@Component
public class DefaultProductPricingCatalog implements ProductPricingCatalog {

    private final ProductRepository products;
    private final ProductTaxRepository productTaxes;

    public DefaultProductPricingCatalog(ProductRepository products, ProductTaxRepository productTaxes) {
        this.products = products;
        this.productTaxes = productTaxes;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductPricingInfo> find(long productId, long countryId) {
        return products.findById(productId).map(product -> {
            Tax tax = productTaxes.findByProductIdAndCountryId(productId, countryId)
                    .map(ProductTax::getTax)
                    .orElse(product.getTax());
            return new ProductPricingInfo(productId, product.isActive(), product.getSalePrice(),
                    tax.getCode(), tax.getRate());
        });
    }
}
