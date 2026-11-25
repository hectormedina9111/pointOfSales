package com.medinasystems.pos.sales.application;

/**
 * The outcome of a charge. {@code created} is false when the idempotency key matched a sale that already existed, which
 * the web layer answers with 200 instead of 201.
 */
public record SaleResult(SaleView sale, boolean created) {
}
