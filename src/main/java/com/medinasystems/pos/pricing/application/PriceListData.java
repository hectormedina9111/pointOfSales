package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.medinasystems.pos.pricing.domain.PricingMethod;

/** Input for creating or changing a price list. */
public record PriceListData(String code, String name, Long countryId, PricingMethod pricingMethod,
                            BigDecimal percent, LocalDate validFrom, LocalDate validTo, short priority,
                            boolean active) {
}
