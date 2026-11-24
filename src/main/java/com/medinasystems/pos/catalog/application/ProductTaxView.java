package com.medinasystems.pos.catalog.application;

import com.medinasystems.pos.catalog.domain.ProductTax;

public record ProductTaxView(Long countryId, Long taxId) {

    public static ProductTaxView from(ProductTax row) {
        return new ProductTaxView(row.getCountryId(), row.getTax().getId());
    }
}
