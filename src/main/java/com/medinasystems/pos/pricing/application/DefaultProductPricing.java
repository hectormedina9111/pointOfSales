package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.pricing.PriceQuote;
import com.medinasystems.pos.pricing.ProductPricing;

/**
 * Answers {@link ProductPricing} with {@link PriceService}, so a sale and the price endpoint never disagree. The
 * customer's list is asked for first through the same resolution, and the branch rules are asked again when it gives
 * no price: the resolution rules are never copied here.
 */
@Component
public class DefaultProductPricing implements ProductPricing {

    private final PriceService prices;

    public DefaultProductPricing(PriceService prices) {
        this.prices = prices;
    }

    @Override
    public PriceQuote quote(long productId, BigDecimal quantity, Long branchId, Long priceListId, LocalDate date) {
        if (priceListId != null) {
            ProductPriceView fromCustomerList = prices.resolve(productId, priceListId, branchId, quantity, date);
            // A base price answer means the customer's list gave no price for this product, quantity and date.
            if (fromCustomerList.priceListId() != null) {
                return toQuote(fromCustomerList);
            }
        }
        return toQuote(prices.resolve(productId, null, branchId, quantity, date));
    }

    private static PriceQuote toQuote(ProductPriceView view) {
        return new PriceQuote(view.unitPrice(), view.taxCode(), view.taxRate(), view.priceListId());
    }
}
