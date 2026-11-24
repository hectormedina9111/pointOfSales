package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.medinasystems.pos.pricing.domain.PriceList;
import com.medinasystems.pos.pricing.domain.PricingMethod;

public record PriceListView(Long id, String code, String name, Long countryId, PricingMethod pricingMethod,
                            BigDecimal percent, LocalDate validFrom, LocalDate validTo, short priority,
                            boolean active) {

    public static PriceListView from(PriceList list) {
        return new PriceListView(list.getId(), list.getCode(), list.getName(), list.getCountryId(),
                list.getPricingMethod(), list.getPercent(), list.getValidFrom(), list.getValidTo(),
                list.getPriority(), list.isActive());
    }
}
