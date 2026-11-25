package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rules of the sales domain: two decimals rounded half up, and the percentage arithmetic of the discounts.
 * Package-private: the rest of the application receives the results, not these helpers.
 */
final class Money {

    static final BigDecimal ZERO = new BigDecimal("0.00");
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    /** Rounds to cents, half up. */
    static BigDecimal scale2(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code base * percent / 100}, rounded to cents, half up. */
    static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * True when {@code part / base} is greater than {@code maxPercent / 100}. The comparison is exact, with no
     * rounding, so a discount of exactly the maximum is allowed and one cent above it is not.
     */
    static boolean exceeds(BigDecimal part, BigDecimal base, BigDecimal maxPercent) {
        return part.signum() > 0 && part.multiply(HUNDRED).compareTo(base.multiply(maxPercent)) > 0;
    }
}
