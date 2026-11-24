package com.medinasystems.pos.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Splits a price that includes tax into its taxable base and its tax. The base is calculated
 * backwards: base = price / (1 + rate / 100), rounded HALF_UP to 2 decimals; tax = price - base.
 */
public record TaxBreakdown(BigDecimal taxableBase, BigDecimal taxAmount) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static TaxBreakdown of(BigDecimal priceWithTax, BigDecimal ratePercent) {
        Objects.requireNonNull(priceWithTax, "price is required");
        Objects.requireNonNull(ratePercent, "rate is required");
        BigDecimal base = priceWithTax.multiply(HUNDRED)
                .divide(HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
        BigDecimal tax = priceWithTax.subtract(base).setScale(2, RoundingMode.HALF_UP);
        return new TaxBreakdown(base, tax);
    }
}
