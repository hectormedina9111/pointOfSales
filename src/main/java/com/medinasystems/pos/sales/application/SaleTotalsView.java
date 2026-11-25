package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;

import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleTotals;

/**
 * The totals of a sale. {@code exemptAmount + taxableAmount15 + taxableAmount18 + otherTaxableAmount + taxTotal = total}.
 * {@code subtotal} is the taxable base before discounts; {@code discountTotal} is tax included.
 */
public record SaleTotalsView(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal exemptAmount,
                             BigDecimal taxableAmount15, BigDecimal taxableAmount18, BigDecimal otherTaxableAmount,
                             BigDecimal taxTotal, BigDecimal total) {

    public static SaleTotalsView from(SaleTotals totals) {
        return new SaleTotalsView(totals.subtotal(), totals.discountTotal(), totals.exemptAmount(),
                totals.taxableAmount15(), totals.taxableAmount18(), totals.otherTaxableAmount(), totals.taxTotal(),
                totals.total());
    }

    public static SaleTotalsView from(Sale sale) {
        return new SaleTotalsView(sale.getSubtotal(), sale.getDiscountTotal(), sale.getExemptAmount(),
                sale.getTaxableAmount15(), sale.getTaxableAmount18(), sale.getOtherTaxableAmount(), sale.getTaxTotal(),
                sale.getTotal());
    }
}
