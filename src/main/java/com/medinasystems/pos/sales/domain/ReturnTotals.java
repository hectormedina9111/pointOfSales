package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * The totals of a return. The taxable bases and the tax add up to the total, as in a sale:
 * {@code exemptAmount + taxableAmount15 + taxableAmount18 + otherTaxableAmount + taxTotal = total}.
 */
public record ReturnTotals(List<CalculatedReturnLine> lines, BigDecimal subtotal, BigDecimal exemptAmount,
                           BigDecimal taxableAmount15, BigDecimal taxableAmount18, BigDecimal otherTaxableAmount,
                           BigDecimal taxTotal, BigDecimal total) {

    public ReturnTotals {
        lines = List.copyOf(lines);
    }
}
