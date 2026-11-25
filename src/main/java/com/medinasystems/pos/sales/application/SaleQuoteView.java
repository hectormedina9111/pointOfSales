package com.medinasystems.pos.sales.application;

import java.util.List;

import com.medinasystems.pos.sales.domain.SaleTotals;

/**
 * The totals of a draft sale, computed by the server and not stored. {@code discountAuthorizationRequired} is true when
 * the discounts are above the maximum and the current user cannot give them alone.
 */
public record SaleQuoteView(List<SaleLineView> lines, SaleTotalsView totals, boolean discountAuthorizationRequired) {

    public static SaleQuoteView from(SaleTotals totals, boolean discountAuthorizationRequired) {
        return new SaleQuoteView(totals.lines().stream().map(line -> SaleLineView.from(line)).toList(),
                SaleTotalsView.from(totals), discountAuthorizationRequired);
    }
}
