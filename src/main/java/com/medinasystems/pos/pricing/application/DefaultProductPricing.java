package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.pricing.PriceQuote;
import com.medinasystems.pos.pricing.ProductPricing;

/** Answers {@link ProductPricing} with {@link PriceService}, so a sale and the price endpoint never disagree. */
@Component
public class DefaultProductPricing implements ProductPricing {

    private final PriceService prices;

    public DefaultProductPricing(PriceService prices) {
        this.prices = prices;
    }

    @Override
    public PriceQuote quote(long productId, BigDecimal quantity, Long branchId, LocalDate date) {
        ProductPriceView view = prices.resolve(productId, null, branchId, quantity, date);
        return new PriceQuote(view.unitPrice(), view.taxCode(), view.taxRate(), view.priceListId());
    }
}
