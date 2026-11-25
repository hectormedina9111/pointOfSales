package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;

/**
 * A line after the totals engine ran. {@code subtotal} is the taxable base of the line after its discounts, and
 * {@code subtotal + taxAmount = total}. {@code discountAmount} includes the line's share of the sale discount.
 */
public record CalculatedLine(int lineNumber, PricedLine line, BigDecimal lineDiscount, BigDecimal saleDiscountShare,
                             BigDecimal discountAmount, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                             boolean exceedsMaxDiscount) {
}
