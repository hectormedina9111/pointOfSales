package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.sales.domain.ReturnTotals;

/** The amounts a return would refund, computed without storing anything. */
public record ReturnQuoteView(BigDecimal total, BigDecimal taxTotal, List<Line> lines) {

    /** One returned line: the quantity, the amount refunded for it and its tax. */
    public record Line(Long saleLineId, BigDecimal quantity, BigDecimal total, BigDecimal taxAmount) {
    }

    public static ReturnQuoteView from(ReturnTotals totals) {
        return new ReturnQuoteView(totals.total(), totals.taxTotal(), totals.lines().stream()
                .map(line -> new Line(line.original().getId(), line.quantity(), line.total(), line.taxAmount()))
                .toList());
    }
}
