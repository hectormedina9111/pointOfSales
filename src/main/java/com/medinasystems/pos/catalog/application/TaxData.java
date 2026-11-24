package com.medinasystems.pos.catalog.application;

import java.math.BigDecimal;

/** Input for creating or changing a tax. The country is set at creation and cannot change. */
public record TaxData(Long countryId, String code, String name, BigDecimal rate, boolean active) {
}
