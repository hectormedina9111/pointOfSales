package com.medinasystems.pos.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Public API of the pricing module for the sales module. It answers the unit price of a product at a branch,
 * with the same rules as the price endpoint, without exposing the price resolution classes.
 */
public interface ProductPricing {

    /**
     * The price of one unit at this quantity, with tax included. The price list is the default list of the branch,
     * or the base price of the product when the branch has none.
     *
     * @param branchId branch that sells; its country and default price list decide the price
     * @param date     day the price applies to; today in the time zone of the country when null
     * @throws com.medinasystems.pos.shared.ResourceNotFoundException when the product does not exist
     * @throws com.medinasystems.pos.shared.BusinessRuleException      INVALID_QUANTITY or PRODUCT_INACTIVE
     */
    PriceQuote quote(long productId, BigDecimal quantity, Long branchId, LocalDate date);
}
