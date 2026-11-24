package com.medinasystems.pos.catalog.application;

import java.math.BigDecimal;

import com.medinasystems.pos.catalog.domain.Tax;

public record TaxView(Long id, Long countryId, String code, String name, BigDecimal rate, boolean active) {

    public static TaxView from(Tax tax) {
        return new TaxView(tax.getId(), tax.getCountryId(), tax.getCode(), tax.getName(), tax.getRate(),
                tax.isActive());
    }
}
