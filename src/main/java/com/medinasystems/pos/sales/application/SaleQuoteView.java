package com.medinasystems.pos.sales.application;

import java.util.List;
import java.util.function.Function;

import com.medinasystems.pos.sales.domain.SaleTotals;

/**
 * The totals of a draft sale, computed by the server and not stored. The two flags tell the screen which authorizations
 * the sale will need before it is saved: {@code discountAuthorizationRequired} when a discount is above the maximum, and
 * {@code priceAuthorizationRequired} when a price was changed. Each is true only when the current user cannot do it alone.
 */
public record SaleQuoteView(List<SaleLineView> lines, SaleTotalsView totals, boolean discountAuthorizationRequired,
                            boolean priceAuthorizationRequired) {

    public static SaleQuoteView from(SaleTotals totals, boolean discountAuthorizationRequired,
                                     boolean priceAuthorizationRequired, Function<Long, String> serialModes) {
        return new SaleQuoteView(totals.lines().stream().map(line -> SaleLineView.from(line, serialModes)).toList(),
                SaleTotalsView.from(totals), discountAuthorizationRequired, priceAuthorizationRequired);
    }
}
