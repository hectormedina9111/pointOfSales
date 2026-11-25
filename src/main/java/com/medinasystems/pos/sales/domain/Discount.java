package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A discount requested by the cashier, on one line or on the whole sale. A percentage is between 0 and 100; an
 * amount is zero or more. Whether it is allowed without a manager is decided by {@link SaleCalculator}.
 */
public record Discount(DiscountType type, BigDecimal value) {

    public Discount {
        Objects.requireNonNull(type, "discount type is required");
        Objects.requireNonNull(value, "discount value is required");
        if (value.signum() < 0 || (type == DiscountType.PERCENTAGE && value.compareTo(Money.HUNDRED) > 0)) {
            throw new BusinessRuleException("INVALID_DISCOUNT", "The discount is out of range");
        }
        value = Money.scale2(value);
    }

    /**
     * The discount the request asks for: null when it asks for none. A type without a value, or a value without a
     * type, is refused.
     */
    public static Discount of(DiscountType type, BigDecimal value) {
        if (type == null && value == null) {
            return null;
        }
        if (type == null || value == null) {
            throw new BusinessRuleException("INVALID_DISCOUNT", "A discount needs both a type and a value");
        }
        return new Discount(type, value);
    }
}
