package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Splits an amount that includes tax into its taxable base and its tax. The base is calculated backwards:
 * {@code base = amount / (1 + rate / 100)}, rounded half up to cents, and {@code tax = amount - base}. The pricing
 * module has the same rule; each module keeps its own copy because modules do not share domain classes.
 */
record TaxSplit(BigDecimal taxableBase, BigDecimal taxAmount) {

    static TaxSplit of(BigDecimal amountWithTax, BigDecimal ratePercent) {
        BigDecimal base = amountWithTax.multiply(Money.HUNDRED)
                .divide(Money.HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
        return new TaxSplit(base, amountWithTax.subtract(base));
    }
}
