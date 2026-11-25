package com.medinasystems.pos.sales.domain;

/**
 * The header of an order: who buys, how it is paid, who authorized the discount and the price changes (null when none
 * was needed) and the notes. The lines and totals come from {@link SaleTotals}.
 */
public record SaleOrder(long customerId, OrderType orderType, Long discountAuthorizedBy, Long priceAuthorizedBy,
                        String notes) {
}
