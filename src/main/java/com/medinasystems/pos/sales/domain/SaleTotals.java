package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * The totals of a sale, as the calculator computed them. The taxable bases and the tax add up to the total:
 * {@code exemptAmount + taxableAmount15 + taxableAmount18 + otherTaxableAmount + taxTotal = total}.
 *
 * @param subtotal                 taxable base of the prices before any discount
 * @param discountTotal            discounts given, tax included: line discounts plus the sale discount
 * @param otherTaxableAmount       taxable base of the rates other than 0, 15 and 18
 * @param exclusive                true when the sale holds a product with an exclusive invoice
 * @param exceedsMaxDiscount       true when a line or the sale discount is above the maximum percentage
 */
public record SaleTotals(List<CalculatedLine> lines, Discount saleDiscount, BigDecimal subtotal,
                         BigDecimal discountTotal, BigDecimal exemptAmount, BigDecimal taxableAmount15,
                         BigDecimal taxableAmount18, BigDecimal otherTaxableAmount, BigDecimal taxTotal,
                         BigDecimal total, boolean exclusive, boolean exceedsMaxDiscount) {

    public SaleTotals {
        lines = List.copyOf(lines);
    }
}
