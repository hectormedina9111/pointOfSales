package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;

/**
 * One returned line. {@code total} is the amount refunded for the returned quantity, tax included; {@code subtotal} is its
 * taxable base and {@code taxAmount} its tax, so {@code subtotal + taxAmount = total}.
 */
public record CalculatedReturnLine(ReturnSourceLine original, BigDecimal quantity, BigDecimal subtotal,
                                   BigDecimal taxAmount, BigDecimal total) {
}
